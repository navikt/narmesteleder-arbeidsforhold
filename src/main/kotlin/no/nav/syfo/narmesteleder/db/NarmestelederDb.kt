package no.nav.syfo.narmesteleder.db

import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederKafkaMessage
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneOffset
import java.util.UUID

class NarmestelederDb(private val database: DatabaseInterface) {
    fun insertOrUpdate(narmesteleder: NarmestelederKafkaMessage) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               insert into narmesteleder(narmeste_leder_id, orgnummer, bruker_fnr, last_update) 
               values (?, ?, ?, ?) on conflict (narmeste_leder_id) do nothing;
            """
            ).use { preparedStatement ->
                preparedStatement.setString(1, narmesteleder.narmesteLederId.toString())
                preparedStatement.setString(2, narmesteleder.orgnummer)
                preparedStatement.setString(3, narmesteleder.fnr)
                preparedStatement.setTimestamp(4, Timestamp.from(narmesteleder.aktivFom.atStartOfDay().toInstant(ZoneOffset.UTC)))
                preparedStatement.executeUpdate()
            }
            connection.commit()
        }
    }

    fun remove(narmesteleder: NarmestelederKafkaMessage) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               delete from narmesteleder where narmeste_leder_id = ?;
            """
            ).use { ps ->
                ps.setString(1, narmesteleder.narmesteLederId.toString())
                ps.executeUpdate()
            }
            connection.commit()
        }
    }

    fun getNarmesteledereToUpdate(): List<NarmestelederDbModel> {
        return database.connection.use { connection ->
            connection.prepareStatement(
                """
                select * from narmesteleder order by last_update limit 1000;
            """
            ).use { ps ->
                ps.executeQuery().toNarmestelederDb()
            }
        }
    }
}

private fun ResultSet.toNarmestelederDb(): List<NarmestelederDbModel> {
    val nlList = mutableListOf<NarmestelederDbModel>()
    while (next()) {
        nlList.add(
            NarmestelederDbModel(
                narmestelederId = UUID.fromString(getString("narmeste_leder_id")),
                lastUpdated = getTimestamp("last_update").toInstant().atOffset(ZoneOffset.UTC),
                fnr = getString("bruker_fnr"),
                orgnummer = getString("orgnummer")
            )
        )
    }
    return nlList
}
