package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.application.metrics.ERROR_COUNTER
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.narmesteleder.NarmestelederService
import no.nav.syfo.narmesteleder.arbeidsforhold.NarmestelederArbeidsforholdUpdateService
import no.nav.syfo.narmesteleder.arbeidsforhold.client.AccessTokenClient
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.narmesteleder.kafka.producer.NarmestelederKafkaProducer
import no.nav.syfo.util.JacksonKafkaDeserializer
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.narmesteleder-arbeidsforhold")
val securelog: Logger = LoggerFactory.getLogger("securelog")

fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(env, applicationState)
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    val kafkaConsumer =
        KafkaConsumer(
            KafkaUtils.getAivenKafkaConfig("narmesteleder-leder-consumer")
                .also {
                    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "100"
                    it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = env.offsetResetPolicy
                }
                .toConsumerConfig("narmesteleder-arbeidsforhold", JacksonKafkaDeserializer::class),
            StringDeserializer(),
            JacksonKafkaDeserializer(NarmestelederLeesahKafkaMessage::class)
        )

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(50, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn(
                        "Retrying for statuscode ${response.status.value}, for url ${request.url}"
                    )
                    true
                } else {
                    false
                }
            }
        }
        install(HttpTimeout) {
            socketTimeoutMillis = 20_000
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 20_000
        }
    }
    val httpClient = HttpClient(Apache, config)

    val database = Database(env, applicationState)
    val narmesteLederDb = NarmestelederDb(database)
    val accessTokenClient =
        AccessTokenClient(env.aadAccessTokenUrl, env.clientId, env.clientSecret, httpClient)

    val arbeidsforholdClient = ArbeidsforholdClient(httpClient = httpClient, url = env.aaregUrl)
    val arbeidsgiverService =
        ArbeidsgiverService(
            arbeidsforholdClient = arbeidsforholdClient,
            accessTokenClient = accessTokenClient,
            scope = env.aaregScope
        )

    val narmestelederKafkaProducer =
        NarmestelederKafkaProducer(
            env.narmestelederTopic,
            KafkaProducer(
                KafkaUtils.getAivenKafkaConfig("narmesteleder-leder-producer")
                    .toProducerConfig(
                        "narmesteleder-arbeidsforhold-producer",
                        JacksonKafkaSerializer::class,
                        StringSerializer::class
                    )
            )
        )

    val narmestelederArbeidsforholdUpdateService =
        NarmestelederArbeidsforholdUpdateService(
            narmestelederDb = narmesteLederDb,
            arbeidsgiverService = arbeidsgiverService,
            narmestelederKafkaProducer = narmestelederKafkaProducer,
            cluster = env.cluster
        )
    val narmestelederService =
        NarmestelederService(
            kafkaConsumer,
            narmesteLederDb,
            applicationState,
            env.narmestelederLeesahTopic,
            narmestelederArbeidsforholdUpdateService
        )

    startBackgroundJob(applicationState) { narmestelederService.start() }
    applicationServer.start()
}

@OptIn(DelicateCoroutinesApi::class)
fun startBackgroundJob(
    applicationState: ApplicationState,
    block: suspend CoroutineScope.() -> Unit
) {
    GlobalScope.launch(Dispatchers.Unbounded) {
        try {
            block()
        } catch (ex: Exception) {
            ERROR_COUNTER.labels("background-task").inc()
            log.error("Error in background task, restarting application", ex)
            applicationState.alive = false
            applicationState.ready = false
        }
    }
}
