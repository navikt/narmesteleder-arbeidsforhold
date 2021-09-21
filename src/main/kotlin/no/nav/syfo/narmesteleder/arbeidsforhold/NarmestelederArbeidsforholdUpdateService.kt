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
import java.util.concurrent.atomic.AtomicInteger
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
        val atomicCounter = AtomicInteger(0)
        val logJob = GlobalScope.launch(Dispatchers.Unbounded) {
            while (true) {
                delay(10_000)
                log.info("Checked : ${atomicCounter.get()}")
            }
        }
        val narmesteleder = narmestelederDb.getNarmesteledereToUpdate()
        var checkedNarmesteleder: List<CheckedNarmesteleder>? = null

        val timeUsed = (
            measureTimeMillis {
                val jobs = narmesteleder.chunked(100).map {
                    GlobalScope.async(context = Dispatchers.Fixed) {
                        it.map {
                            (try {
                                CheckedNarmesteleder(it, checkNl(it), failed = false)
                            } catch (ex: Exception) {
                                CheckedNarmesteleder(it, true, failed = true)
                            }).also { atomicCounter.incrementAndGet() }
                        }
                    }
                }
                checkedNarmesteleder = jobs.awaitAll().flatten()
            } / 1000.0
        )
        val valid = checkedNarmesteleder!!.filter { it.valid && !it.failed }.size
        val unvalid = checkedNarmesteleder!!.filter { !it.valid && !it.failed }.size
        val failed = checkedNarmesteleder!!.filter { it.failed }.size
        log.info("Checked ${narmesteleder.size}, gyldig: $valid, ugyldig: $unvalid, failed: $failed from: ${narmesteleder.first().lastUpdated}, to: ${narmesteleder.last().lastUpdated}, time used (seconds): $timeUsed")
        logJob.cancel()
    }

    private suspend fun checkNl(narmesteleder: NarmestelederDbModel): Boolean {
        val arbeidsforhold = arbeidsgiverService.getArbeidsgivere(narmesteleder.fnr)
        return arbeidsforhold.any { it.orgnummer == narmesteleder.orgnummer }
    }
}
