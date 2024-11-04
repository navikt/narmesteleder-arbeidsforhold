package no.nav.syfo.narmesteleder.arbeidsforhold

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.syfo.TestDb
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import no.nav.syfo.narmesteleder.arbeidsforhold.service.ArbeidsgiverService
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.narmesteleder.db.getNarmesteledereToUpdate
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.narmesteleder.kafka.producer.NarmestelederKafkaProducer
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NarmestelederArbeidsforholdUpdateServiceTest {
    private val database = NarmestelederDb(TestDb.database)
    private val arbeidsgiverService = mockk<ArbeidsgiverService>()
    private val narmestelederKafkaProducer = mockk<NarmestelederKafkaProducer>(relaxed = true)
    private val narmestelederArbeidsforholdUpdateService =
        NarmestelederArbeidsforholdUpdateService(
            database,
            arbeidsgiverService,
            narmestelederKafkaProducer,
            "prod-gcp",
        )

    val narmesteleder =
        NarmestelederLeesahKafkaMessage(
            narmesteLederId = UUID.randomUUID(),
            fnr = "1",
            orgnummer = "1",
            narmesteLederEpost = "test@nav.no",
            narmesteLederFnr = "2",
            narmesteLederTelefonnummer = "12345678",
            aktivFom = LocalDate.of(2020, 1, 1),
            arbeidsgiverForskutterer = null,
            aktivTom = null,
            timestamp = OffsetDateTime.now(),
        )

    @BeforeEach
    fun beforeTest() {
        TestDb.clearAllData()
        clearMocks(arbeidsgiverService, narmestelederKafkaProducer)
    }

    @Test
    internal fun `Test update narmesteleder should update last_update`() {
        database.insertOrUpdate(narmesteleder)

        coEvery { arbeidsgiverService.getArbeidsgivere("1") } returns
            listOf(Arbeidsgiverinfo("1", null))

        val beforeUpdate = TestDb.getNarmesteleder().first()
        runBlocking {
            narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
            val afterUpdate = TestDb.getNarmesteleder().first()

            afterUpdate.lastUpdated.isAfter(beforeUpdate.lastUpdated) shouldBeEqualTo true

            verify(exactly = 0) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }
    }

    @Test
    internal fun `Test update narmesteleder should send avbrutt kafka melding`() {
        database.insertOrUpdate(narmesteleder)

        coEvery { arbeidsgiverService.getArbeidsgivere("1") } returns
            listOf(Arbeidsgiverinfo("2", null))

        val beforeUpdate = TestDb.getNarmesteleder().first()
        runBlocking {
            narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
            val afterUpdate = TestDb.getNarmesteleder().first()

            afterUpdate.lastUpdated.isAfter(beforeUpdate.lastUpdated) shouldBeEqualTo true

            verify(exactly = 1) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }
    }

    @Test
    internal fun `Test update narmesteleder should not update when aareg fails`() {
        database.insertOrUpdate(narmesteleder)

        coEvery { arbeidsgiverService.getArbeidsgivere("1") } throws RuntimeException("error")

        val beforeUpdate = TestDb.getNarmesteleder().first()
        runBlocking {
            narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
            val afterUpdate = TestDb.getNarmesteleder().first()

            afterUpdate.lastUpdated shouldBeEqualTo beforeUpdate.lastUpdated

            verify(exactly = 0) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }
    }

    @Test
    internal fun `Test update narmesteleder should not update when aareg fails avbrutt`() {
        database.insertOrUpdate(narmesteleder)

        coEvery { arbeidsgiverService.getArbeidsgivere("1") } returns
            listOf(Arbeidsgiverinfo("2", null))
        every { narmestelederKafkaProducer.sendNlAvbrutt(any()) } throws RuntimeException("error")

        val beforeUpdate = TestDb.getNarmesteleder().first()
        runBlocking {
            narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
            val afterUpdate = TestDb.getNarmesteleder().first()

            afterUpdate.lastUpdated shouldBeEqualTo beforeUpdate.lastUpdated

            verify(exactly = 1) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }
    }

    @Test
    internal fun `Test update narmesteleder Should not update recently updated`() {
        database.insertOrUpdate(narmesteleder.copy(aktivFom = LocalDate.now().minusDays(1)))

        val beforeUpdate = TestDb.getNarmesteleder().first()
        runBlocking {
            narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
            val afterUpdate = TestDb.getNarmesteleder().first()

            afterUpdate.lastUpdated shouldBeEqualTo beforeUpdate.lastUpdated

            coVerify(exactly = 0) { arbeidsgiverService.getArbeidsgivere(any()) }
            verify(exactly = 0) { narmestelederKafkaProducer.sendNlAvbrutt(any()) }
        }
    }

    @Test
    internal fun `Test update narmesteleder Test batch update`() {
        (0 until 100).forEach {
            database.insertOrUpdate(narmesteleder.copy(narmesteLederId = UUID.randomUUID()))
        }
        coEvery { arbeidsgiverService.getArbeidsgivere("1") } returns
            listOf(Arbeidsgiverinfo("1", null))

        runBlocking {
            narmestelederArbeidsforholdUpdateService.updateNarmesteledere()
            database.use {
                getNarmesteledereToUpdate(OffsetDateTime.now().minusMonths(1)).size shouldBeEqualTo
                    0
            }
        }
    }
}
