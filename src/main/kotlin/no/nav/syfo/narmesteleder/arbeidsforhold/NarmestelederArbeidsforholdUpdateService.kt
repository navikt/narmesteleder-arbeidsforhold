package no.nav.syfo.narmesteleder.arbeidsforhold

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import no.nav.syfo.application.metrics.CHECKED_NL_COUNTER
import no.nav.syfo.application.metrics.ERROR_COUNTER
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.narmesteleder.db.getNarmesteledereToUpdate
import no.nav.syfo.narmesteleder.db.updateLastUpdate
import no.nav.syfo.narmesteleder.kafka.producer.NarmestelederKafkaProducer
import java.time.OffsetDateTime
import java.time.ZoneOffset

class NarmestelederArbeidsforholdUpdateService(
    private val narmestelederDb: NarmestelederDb,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val narmestelederKafkaProducer: NarmestelederKafkaProducer,
    private val cluster: String
) {

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun updateNarmesteledere() {
        narmestelederDb.use {
            val lastUpdateLimit = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1)
            val narmesteleder = getNarmesteledereToUpdate(lastUpdateLimit)
            if (narmesteleder.isEmpty()) {
                return@use
            }
            val checkedNarmesteleder: List<CheckedNarmesteleder> = narmesteleder.chunked(100).map {
                GlobalScope.async(context = Dispatchers.Fixed) {
                    checkNarmesteleder(it)
                }
            }.awaitAll().flatten()

            updateLastUpdate(checkedNarmesteleder.filter { !it.failed })
        }
    }

    private suspend fun checkNarmesteleder(
        it: List<NarmestelederDbModel>
    ) = it.mapNotNull {
        try {
            val checkedNarmesteleder = CheckedNarmesteleder(it, checkNl(it), failed = false)
            when (checkedNarmesteleder.valid) {
                true -> {
                    CHECKED_NL_COUNTER.labels("valid").inc()
                    checkedNarmesteleder
                }
                else -> {
                    narmestelederKafkaProducer.sendNlAvbrutt(checkedNarmesteleder.narmestelederDbModel)
                    CHECKED_NL_COUNTER.labels("invalid").inc()
                    checkedNarmesteleder
                }
            }
        } catch (ex: Exception) {
            if (cluster == "dev-gcp") {
                log.warn("Sletter nl-kobling fordi oppslag feilet og dette er dev")
                narmestelederDb.remove(it.narmestelederId)
                null
            } else {
                CHECKED_NL_COUNTER.labels("failed").inc()
                CheckedNarmesteleder(it, true, failed = true)
            }
        }
    }

    private suspend fun checkNl(narmesteleder: NarmestelederDbModel): Boolean {
        try {
            val arbeidsforhold = arbeidsgiverService.getArbeidsgivere(narmesteleder.brukerFnr)
            return arbeidsforhold.any { it.orgnummer == narmesteleder.orgnummer }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av arbeidsgivere for narmestelederId ${narmesteleder.narmestelederId}: ${e.message}", e)
            ERROR_COUNTER.labels("arbeidsforhold").inc()
            throw e
        }
    }
}
