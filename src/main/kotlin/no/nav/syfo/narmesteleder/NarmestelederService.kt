package no.nav.syfo.narmesteleder

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederKafkaMessage
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class NarmestelederService(
    private val kafkaConsumer: KafkaConsumer<String, NarmestelederKafkaMessage>,
    private val narmestelederDb: NarmestelederDb,
    private val applicationState: ApplicationState,
    private val narmestelederLeesahTopic: String
) {
    fun start() {
        kafkaConsumer.subscribe(listOf(narmestelederLeesahTopic))
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofMillis(10_000)).forEach {
                updateNl(it.value())
            }
        }
    }

    private fun updateNl(narmesteleder: NarmestelederKafkaMessage) {
        when (narmesteleder.aktivTom) {
            null -> narmestelederDb.insertOrUpdate(narmesteleder)
            else -> narmestelederDb.remove(narmesteleder)
        }
    }
}
