package no.nav.syfo.narmesteleder.kafka.producer

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.application.metrics.ERROR_COUNTER
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.narmesteleder.kafka.model.KafkaMetadata
import no.nav.syfo.narmesteleder.kafka.model.NlAvbrutt
import no.nav.syfo.narmesteleder.kafka.model.NlResponseKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class NarmestelederKafkaProducer(
    private val topic: String,
    private val kafkaProducer: KafkaProducer<String, NlResponseKafkaMessage>
) {
    fun sendNlAvbrutt(narmestelederDbModel: NarmestelederDbModel) {
        try {
            val nlResponseMessage =
                NlResponseKafkaMessage(
                    kafkaMetadata =
                        KafkaMetadata(
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                            source = "narmesteleder-arbeidsforhold"
                        ),
                    nlAvbrutt =
                        NlAvbrutt(
                            orgnummer = narmestelederDbModel.orgnummer,
                            sykmeldtFnr = narmestelederDbModel.brukerFnr,
                            aktivTom = OffsetDateTime.now(ZoneOffset.UTC)
                        )
                )
            kafkaProducer
                .send(
                    ProducerRecord(topic, nlResponseMessage.nlAvbrutt.orgnummer, nlResponseMessage)
                )
                .get()
        } catch (e: Exception) {
            ERROR_COUNTER.labels("kafka").inc()
            throw e
        }
    }
}
