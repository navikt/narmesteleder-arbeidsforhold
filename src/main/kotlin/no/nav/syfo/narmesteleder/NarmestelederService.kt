package no.nav.syfo.narmesteleder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.arbeidsforhold.NarmestelederArbeidsforholdUpdateService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class NarmestelederService(
    private val kafkaConsumer: KafkaConsumer<String, NarmestelederLeesahKafkaMessage>,
    private val narmestelederDb: NarmestelederDb,
    private val applicationState: ApplicationState,
    private val narmestelederLeesahTopic: String,
    private val narmestelederArbeidsforholdUpdateService: NarmestelederArbeidsforholdUpdateService
) {

    private var inserts = 0
    private var deletes = 0
    private var logTotal = -1
    suspend fun start() {
        log.info("Starting jobs in 60 seconds, to allow pods to terminate before start")
        delay(60_000)
        kafkaConsumer.subscribe(listOf(narmestelederLeesahTopic))
        narmestelederArbeidsforholdUpdateService.startLogging()
        startLogging()
        while (applicationState.ready) {
            narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
            kafkaConsumer.poll(Duration.ofMillis(1000)).forEach {
                updateNl(it.value())
            }
        }
    }

    private fun updateNl(narmesteleder: NarmestelederLeesahKafkaMessage) {
        when (narmesteleder.aktivTom) {
            null -> {
                narmestelederDb.insertOrUpdate(narmesteleder)
                inserts += 1
            }
            else -> {
                narmestelederDb.remove(narmesteleder)
                deletes += 1
            }
        }
    }

    private fun startLogging() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (true) {
                val total = inserts + deletes
                if (logTotal != total) {
                    log.info("New nl-skjema $inserts, deleted nl: $deletes")
                    logTotal = total
                }
                delay(60_000)
            }
        }
    }
}
