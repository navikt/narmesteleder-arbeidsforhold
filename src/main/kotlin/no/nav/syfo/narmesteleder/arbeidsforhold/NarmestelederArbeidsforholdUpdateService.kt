package no.nav.syfo.narmesteleder.arbeidsforhold

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.startBackgroundJob
import no.nav.syfo.util.Unbounded
import kotlin.system.measureTimeMillis

class NarmestelederArbeidsforholdUpdateService(
    private val applicationState: ApplicationState,
    private val narmestelederDb: NarmestelederDb,
    private val arbeidsgiverService: ArbeidsgiverService
) {

    fun start() {
        startBackgroundJob(applicationState) {
            startUpdate()
        }
    }

    private suspend fun startUpdate() {
        var gyldig = 0
        var ugyldig = 0
        val logJob = GlobalScope.launch(Dispatchers.Unbounded) {
            while (true) {
                delay(10_000)
                log.info("checked ${gyldig + ugyldig} narmesteledere")
            }
        }
        val narmesteleder = narmestelederDb.getNarmesteledereToUpdate()
        val timeUsed = (
            measureTimeMillis {
                narmesteleder.forEach {
                    if (checkNl(it)) {
                        gyldig += 1
                    } else {
                        ugyldig += 1
                    }
                }
            } / 1000.0
            )

        log.info("Checked ${narmesteleder.size}, gyldig: $gyldig, ugyldig: $ugyldig, from: ${narmesteleder.first().lastUpdated}, to: ${narmesteleder.last().lastUpdated}, time used (seconds): $timeUsed")
        logJob.cancel()
    }

    private suspend fun checkNl(narmesteleder: NarmestelederDbModel): Boolean {
        val arbeidsforhold = arbeidsgiverService.getArbeidsgivere(narmesteleder.fnr)
        return arbeidsforhold.any { it.orgnummer == narmesteleder.orgnummer }
    }
}
