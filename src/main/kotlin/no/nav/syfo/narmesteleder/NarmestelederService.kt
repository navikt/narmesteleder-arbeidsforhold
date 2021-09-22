package no.nav.syfo.narmesteleder

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class NarmestelederService(
    private val kafkaConsumer: KafkaConsumer<String, NarmestelederLeesahKafkaMessage>,
    private val narmestelederDb: NarmestelederDb,
    private val applicationState: ApplicationState,
    private val narmestelederLeesahTopic: String
) {
    fun start() {
        kafkaConsumer.subscribe(listOf(narmestelederLeesahTopic))
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofMillis(10_000)).forEach {
                log.info("handling nl skjema for ${it.partition()}:${it.offset()}")
                updateNl(it.value())
            }
        }
    }

    private fun updateNl(narmesteleder: NarmestelederLeesahKafkaMessage) {
        when (narmesteleder.aktivTom) {
            null -> narmestelederDb.insertOrUpdate(narmesteleder)
            else -> narmestelederDb.remove(narmesteleder)
        }
    }
}
