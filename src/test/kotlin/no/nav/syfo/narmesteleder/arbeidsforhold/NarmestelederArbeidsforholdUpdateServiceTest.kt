package no.nav.syfo.narmesteleder.arbeidsforhold

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.TestDb
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.db.getNarmesteledereToUpdate
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.narmesteleder.kafka.producer.NarmestelederKafkaProducer
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class NarmestelederArbeidsforholdUpdateServiceTest : Spek({
    val database = NarmestelederDb(TestDb.database)
    val arbeidsgiverService = mockk<ArbeidsgiverService>()
    val narmestelederKafkaProducer = mockk<NarmestelederKafkaProducer>(relaxed = true)
    val narmestelederArbeidsforholdUpdateService = NarmestelederArbeidsforholdUpdateService(
        ApplicationState(true, true),
        database,
        arbeidsgiverService,
        narmestelederKafkaProducer
    )

    val narmesteleder = NarmestelederLeesahKafkaMessage(
        narmesteLederId = UUID.randomUUID(),
        fnr = "1",
        orgnummer = "1",
        narmesteLederEpost = "test@nav.no",
        narmesteLederFnr = "2",
        narmesteLederTelefonnummer = "12345678",
        aktivFom = LocalDate.of(2020, 1, 1),
        arbeidsgiverForskutterer = null,
        aktivTom = null,
        timestamp = OffsetDateTime.now()
    )

    beforeEachTest {
        TestDb.clearAllData()
        clearMocks(arbeidsgiverService, narmestelederKafkaProducer)
    }

    describe("Test update narmesteleder") {
        it("should update last_update") {
            database.insertOrUpdate(narmesteleder)

            coEvery { arbeidsgiverService.getArbeidsgivere("1") } returns listOf(Arbeidsgiverinfo("1", null))

            runBlocking {

                val beforeUpdate = TestDb.getNarmesteleder().first()
                narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
                val afterUpdate = TestDb.getNarmesteleder().first()

                afterUpdate.lastUpdated.isAfter(beforeUpdate.lastUpdated) shouldBeEqualTo true
            }
            verify(exactly = 0) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }
        it("should send avbrutt kafka melding") {
            database.insertOrUpdate(narmesteleder)

            coEvery { arbeidsgiverService.getArbeidsgivere("1") } returns listOf(Arbeidsgiverinfo("2", null))

            runBlocking {

                val beforeUpdate = TestDb.getNarmesteleder().first()
                narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
                val afterUpdate = TestDb.getNarmesteleder().first()

                afterUpdate.lastUpdated.isAfter(beforeUpdate.lastUpdated) shouldBeEqualTo true
            }
            verify(exactly = 1) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }

        it("should not update when aareg fails") {
            database.insertOrUpdate(narmesteleder)

            coEvery { arbeidsgiverService.getArbeidsgivere("1") } throws RuntimeException("error")

            runBlocking {

                val beforeUpdate = TestDb.getNarmesteleder().first()
                narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
                val afterUpdate = TestDb.getNarmesteleder().first()

                afterUpdate.lastUpdated shouldBeEqualTo beforeUpdate.lastUpdated
            }
            verify(exactly = 0) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }
        it("should not update when aareg fails") {
            database.insertOrUpdate(narmesteleder)

            coEvery { arbeidsgiverService.getArbeidsgivere("1") } returns listOf(Arbeidsgiverinfo("2", null))
            every { narmestelederKafkaProducer.sendNlAvbrutt(any()) } throws RuntimeException("error")
            runBlocking {

                val beforeUpdate = TestDb.getNarmesteleder().first()
                narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
                val afterUpdate = TestDb.getNarmesteleder().first()

                afterUpdate.lastUpdated shouldBeEqualTo beforeUpdate.lastUpdated
            }
            verify(exactly = 1) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }

        it("Should not update recently updated") {
            database.insertOrUpdate(narmesteleder.copy(aktivFom = LocalDate.now().minusDays(1)))

            runBlocking {

                val beforeUpdate = TestDb.getNarmesteleder().first()
                narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
                val afterUpdate = TestDb.getNarmesteleder().first()

                afterUpdate.lastUpdated shouldBeEqualTo beforeUpdate.lastUpdated
            }
            coVerify(exactly = 0) { arbeidsgiverService.getArbeidsgivere(any()) }
            verify(exactly = 0) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }

        it("Test batch update") {
            (0 until 100).forEach {
                database.insertOrUpdate(narmesteleder.copy(narmesteLederId = UUID.randomUUID()))
            }
            coEvery { arbeidsgiverService.getArbeidsgivere("1") } returns listOf(Arbeidsgiverinfo("1", null))
            runBlocking {
                narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
                database.use {
                    getNarmesteledereToUpdate(OffsetDateTime.now().minusMonths(1)).size shouldBeEqualTo 0
                }
            }
        }
    }
})
