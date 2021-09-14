package no.nav.syfo.narmesteleder.db

import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.narmesteleder.kafka.model.NarmestelederKafkaMessage

class NarmestelederDb(private val database: DatabaseInterface) {
    fun insertOrUpdate(narmesteleder: NarmestelederKafkaMessage) {
        database.connection.use { connection ->
            connection.prepareStatement(
                """
               insert into narmesteleder(narmeste_leder_id, orgnummer, bruker_fnr) 
               values (?, ?, ?) on conflict (narmeste_leder_id) do nothing;
            """
            ).use { preparedStatement ->
                preparedStatement.setString(1, narmesteleder.narmesteLederId.toString())
                preparedStatement.setString(2, narmesteleder.orgnummer)
                preparedStatement.setString(3, narmesteleder.fnr)
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
}
