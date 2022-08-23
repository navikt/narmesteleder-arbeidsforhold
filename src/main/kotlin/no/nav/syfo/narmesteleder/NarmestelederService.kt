package no.nav.syfo.narmesteleder

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.NL_TOPIC_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.arbeidsforhold.NarmestelederArbeidsforholdUpdateService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class NarmestelederService(
    private val kafkaConsumer: KafkaConsumer<String, NarmestelederLeesahKafkaMessage>,
    private val narmestelederDb: NarmestelederDb,
    private val applicationState: ApplicationState,
    private val narmestelederLeesahTopic: String,
    private val narmestelederArbeidsforholdUpdateService: NarmestelederArbeidsforholdUpdateService
) {
    suspend fun start() {
        log.info("Starting jobs in 60 seconds, to allow pods to terminate before start")
        delay(60_000)
        kafkaConsumer.subscribe(listOf(narmestelederLeesahTopic))
        while (applicationState.ready) {
            narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
            kafkaConsumer.poll(Duration.ofMillis(10_000)).forEach {
                updateNl(it.value())
            }
        }
    }

    private fun updateNl(narmesteleder: NarmestelederLeesahKafkaMessage) {
        when (narmesteleder.aktivTom) {
            null -> {
                narmestelederDb.insertOrUpdate(narmesteleder)
                NL_TOPIC_COUNTER.labels("ny").inc()
            }
            else -> {
                narmestelederDb.remove(narmesteleder.narmesteLederId)
                NL_TOPIC_COUNTER.labels("avbrutt").inc()
            }
        }
    }
}
