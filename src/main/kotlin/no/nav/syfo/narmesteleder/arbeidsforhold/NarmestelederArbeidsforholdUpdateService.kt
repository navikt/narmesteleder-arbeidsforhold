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
import no.nav.syfo.startBackgroundJob
import no.nav.syfo.util.Unbounded

class NarmestelederArbeidsforholdUpdateService(
    private val applicationState: ApplicationState,
    private val narmestelederDb: NarmestelederDb,
    private val arbeidsgiverService: ArbeidsgiverService,
    private val narmestelederKafkaProducer: NarmestelederKafkaProducer,
) {

    fun start() {
        startBackgroundJob(applicationState) {
            startUpdate()
        }
    }
    private var valid = 0
    private var invalid = 0
    private var failed = 0

    private val nlIds: List<String> = emptyList()

    private suspend fun startUpdate() {

        GlobalScope.launch(Dispatchers.Unbounded) {
            while (true) {
                delay(60_000)
                log.info("Checked ${valid + invalid + failed}, valid: $valid, invalid: $invalid, failed: $failed")
            }
        }

        while (applicationState.ready) {
            updateNarmesteledere()
        }
    }

    suspend fun updateNarmesteledere() {
        narmestelederDb.use {
            val narmesteleder = getNarmesteledereToUpdate()
            val checkedNarmesteleder: List<CheckedNarmesteleder> = narmesteleder.chunked(100).map {
                GlobalScope.async(context = Dispatchers.Fixed) {
                    checkNarmesteleder(it)
                }
            }.awaitAll().flatten()

            valid += checkedNarmesteleder.filter { it.valid && !it.failed }.size
            invalid += checkedNarmesteleder.filter { !it.valid && !it.failed }.size
            failed += checkedNarmesteleder.filter { it.failed }.size

            checkedNarmesteleder.filter { !it.failed }
                .forEach { updateLastUpdate(it) }
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
            CheckedNarmesteleder(it, true, failed = true)
        }
    }

    private suspend fun checkNl(narmesteleder: NarmestelederDbModel): Boolean {
        val arbeidsforhold = arbeidsgiverService.getArbeidsgivere(narmesteleder.brukerFnr)
        return arbeidsforhold.any { it.orgnummer == narmesteleder.orgnummer }
    }
}
