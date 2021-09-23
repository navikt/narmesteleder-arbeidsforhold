package no.nav.syfo.narmesteleder.arbeidsforhold

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.narmesteleder.db.getNarmesteledereToUpdate
import no.nav.syfo.narmesteleder.db.updateLastUpdate
import no.nav.syfo.narmesteleder.kafka.producer.NarmestelederKafkaProducer
import no.nav.syfo.util.Unbounded
import java.time.OffsetDateTime
import java.time.ZoneOffset

class NarmestelederArbeidsforholdUpdateService(
    private val applicationState: ApplicationState,
    private val narmestelederDb: NarmestelederDb,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val narmestelederKafkaProducer: NarmestelederKafkaProducer,
) {
    private var valid = 0
    private var invalid = 0
    private var failed = 0
    private var lastUpdateTimestamp: OffsetDateTime = OffsetDateTime.MIN
    private var totalLastLog = -1
    private var firstError = true
    suspend fun startLogging() {
        GlobalScope.launch(Dispatchers.Unbounded) {
            while (true) {
                val total = (valid + invalid + failed)
                if (totalLastLog != total) {
                    log.info("Checked ${valid + invalid + failed}, valid: $valid, invalid: $invalid, failed: $failed, lastUpdate: $lastUpdateTimestamp")
                    totalLastLog = total
                }
                delay(60_000)
            }
        }
    }

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

            valid += checkedNarmesteleder.filter { it.valid && !it.failed }.size
            invalid += checkedNarmesteleder.filter { !it.valid && !it.failed }.size
            failed += checkedNarmesteleder.filter { it.failed }.size
            lastUpdateTimestamp = narmesteleder.last().lastUpdated
            updateLastUpdate(checkedNarmesteleder.filter { !it.failed })
        }
    }

    private suspend fun checkNarmesteleder(
        it: List<NarmestelederDbModel>
    ) = it.map {
        try {
            val checkedNarmesteleder = CheckedNarmesteleder(it, checkNl(it), failed = false)
            when (checkedNarmesteleder.valid) {
                true -> checkedNarmesteleder
                else -> {
                    narmestelederKafkaProducer.sendNlAvbrutt(checkedNarmesteleder.narmestelederDbModel)
                    checkedNarmesteleder
                }
            }
        } catch (ex: Exception) {
            if (firstError) {
                log.error("Failed to check NL", ex)
                firstError = false
            }
            CheckedNarmesteleder(it, true, failed = true)
        }
    }

    private suspend fun checkNl(narmesteleder: NarmestelederDbModel): Boolean {
        val arbeidsforhold = arbeidsgiverService.getArbeidsgivere(narmesteleder.brukerFnr)
        return arbeidsforhold.any { it.orgnummer == narmesteleder.orgnummer }
    }
}
