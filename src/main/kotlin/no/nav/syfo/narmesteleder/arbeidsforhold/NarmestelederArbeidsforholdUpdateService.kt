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
                log.info("checked fortsatt gyldig: $gyldig ugyldig: $ugyldig")
            }
        }
        val narmesteleder = narmestelederDb.getNarmesteledereToUpdate()
        var checkedNarmesteleder: List<CheckedNarmesteleder>? = null
        val timeUsed = (
            measureTimeMillis {
                val jobs = narmesteleder.map {
                    GlobalScope.async(context = Dispatchers.Fixed) {
                        val valid = checkNl(it)
                        if (valid) {
                            gyldig += 1
                        } else {
                            log.info("Should be disabled ${it.narmestelederId}")
                            ugyldig += 1
                        }
                        CheckedNarmesteleder(it, valid)
                    }
                }
                checkedNarmesteleder = jobs.awaitAll().map { it }
            } / 1000.0
            )
        val valid = checkedNarmesteleder!!.filter { it.valid }
        val unvalid = checkedNarmesteleder!!.filter { !it.valid }
        log.info("Checked ${narmesteleder.size}, gyldig: $valid, ugyldig: $unvalid, from: ${narmesteleder.first().lastUpdated}, to: ${narmesteleder.last().lastUpdated}, time used (seconds): $timeUsed")
        logJob.cancel()
    }

    private suspend fun checkNl(narmesteleder: NarmestelederDbModel): Boolean {
        val arbeidsforhold = arbeidsgiverService.getArbeidsgivere(narmesteleder.fnr)
        return arbeidsforhold.any { it.orgnummer == narmesteleder.orgnummer }
    }
}
