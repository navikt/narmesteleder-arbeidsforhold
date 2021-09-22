package no.nav.syfo.narmesteleder.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.syfo.TestDb
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage
import no.nav.syfo.util.Unbounded
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.Connection
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class NarmestelederDbTest : Spek({
    val database = NarmestelederDb(TestDb.database)

    val narmesteleder = NarmestelederLeesahKafkaMessage(
        narmesteLederId = UUID.randomUUID(),
        fnr = "1",
        orgnummer = "1",
        narmesteLederEpost = "test@nav.no",
        narmesteLederFnr = "2",
        narmesteLederTelefonnummer = "12345678",
        aktivFom = LocalDate.now(),
        arbeidsgiverForskutterer = null,
        aktivTom = null,
        timestamp = OffsetDateTime.now()
    )

    beforeEachTest {
        TestDb.clearAllData()
    }

    describe("Test database locking") {
        it("should not wait for locking") {

            database.insertOrUpdate(narmesteleder)
            runBlocking {
                database.use {
                    val narmesteledere = getNarmesteledereToUpdate()
                }
                database.remove(narmesteleder)

                database.use {
                    getNarmesteledereToUpdate().size shouldBeEqualTo 0
                }
            }
        }

        it("should be locking") {
            database.insertOrUpdate(narmesteleder)
            var locking = false
            val job = GlobalScope.launch(Dispatchers.Unbounded) {
                database.use {
                    val narmesteledere = getNarmesteledereToUpdate()
                    log.info("locking")
                    locking = true
                    delay(10)
                }
                log.info("Done locking")
            }
            val job2 = GlobalScope.launch(Dispatchers.Unbounded) {
                while (!locking) {
                }
                log.info("removing")
                database.remove(narmesteleder)
                log.info("removed")
            }

            runBlocking {
                job2.join()
                job.join()
                database.use {
                    getNarmesteledere().size shouldBeEqualTo 0
                }
            }
        }
    }
})

fun Connection.getNarmesteledere(): List<NarmestelederDbModel> {
    return prepareStatement(
        """
                    select * from narmesteleder order by last_update limit 100 ;
                """
    ).use { ps ->
        ps.executeQuery().toNarmestelederDb()
    }
}
