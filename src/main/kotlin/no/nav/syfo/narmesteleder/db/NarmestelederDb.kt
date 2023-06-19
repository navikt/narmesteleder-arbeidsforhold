package no.nav.syfo.narmesteleder.db

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.narmesteleder.arbeidsforhold.CheckedNarmesteleder
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederLeesahKafkaMessage

class NarmestelederDb(private val database: DatabaseInterface) {

    suspend fun use(block: suspend Connection.() -> Unit) {
        database.connection.use { connection ->
            try {
                block(connection)
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                log.error("database transaction failed, doing rollback", ex)
            }
        }
    }

    fun insertOrUpdate(narmesteleder: NarmestelederLeesahKafkaMessage) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
               insert into narmesteleder(narmeste_leder_id, orgnummer, bruker_fnr, last_update) 
               values (?, ?, ?, ?) on conflict (narmeste_leder_id) do nothing ;
            """
                )
                .use { preparedStatement ->
                    preparedStatement.setString(1, narmesteleder.narmesteLederId.toString())
                    preparedStatement.setString(2, narmesteleder.orgnummer)
                    preparedStatement.setString(3, narmesteleder.fnr)
                    preparedStatement.setTimestamp(
                        4,
                        Timestamp.from(
                            narmesteleder.aktivFom.atStartOfDay().toInstant(ZoneOffset.UTC)
                        )
                    )
                    preparedStatement.executeUpdate()
                }
            connection.commit()
        }
    }

    fun remove(narmesteLederId: UUID) {
        database.connection.use { connection ->
            connection
                .prepareStatement(
                    """
               delete from narmesteleder where narmeste_leder_id = ?;
            """
                )
                .use { ps ->
                    ps.setString(1, narmesteLederId.toString())
                    ps.executeUpdate()
                }
            connection.commit()
        }
    }
}

fun Connection.updateLastUpdate(narmesteledere: List<CheckedNarmesteleder>) {
    prepareStatement(
            """
        update narmesteleder set last_update = ? where narmeste_leder_id = ?;
    """
        )
        .use { ps ->
            narmesteledere.forEach {
                ps.setTimestamp(1, Timestamp.from(Instant.now()))
                ps.setString(2, it.narmestelederDbModel.narmestelederId.toString())
                ps.addBatch()
            }
            ps.executeBatch()
        }
}

fun Connection.updateLastUpdate(narmesteleder: CheckedNarmesteleder) {
    prepareStatement(
            """
        update narmesteleder set last_update = ? where narmeste_leder_id = ?;
    """
        )
        .use {
            it.setTimestamp(1, Timestamp.from(Instant.now()))
            it.setString(2, narmesteleder.narmestelederDbModel.narmestelederId.toString())
            it.executeUpdate()
        }
}

fun Connection.getNarmesteledereToUpdate(
    lastUpdateLimit: OffsetDateTime
): List<NarmestelederDbModel> {
    return prepareStatement(
            """
                    select * from narmesteleder where last_update < ? order by last_update limit 1000 for update skip locked ;
                """
        )
        .use { ps ->
            ps.setTimestamp(1, Timestamp.from(lastUpdateLimit.toInstant()))
            ps.executeQuery().toNarmestelederDb()
        }
}

fun ResultSet.toNarmestelederDb(): List<NarmestelederDbModel> {
    val nlList = mutableListOf<NarmestelederDbModel>()
    while (next()) {
        nlList.add(
            NarmestelederDbModel(
                narmestelederId = UUID.fromString(getString("narmeste_leder_id")),
                lastUpdated = getTimestamp("last_update").toInstant().atOffset(ZoneOffset.UTC),
                brukerFnr = getString("bruker_fnr"),
                orgnummer = getString("orgnummer")
            )
        )
    }
    return nlList
}
