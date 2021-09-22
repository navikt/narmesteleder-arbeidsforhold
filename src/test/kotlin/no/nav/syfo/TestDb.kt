package no.nav.syfo

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.narmesteleder.db.NarmestelederDbModel
import no.nav.syfo.narmesteleder.db.toNarmestelederDb
import org.testcontainers.containers.PostgreSQLContainer

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDb {
    companion object {
        val database: DatabaseInterface
        val psqlContainer = PsqlContainer()
            .withExposedPorts(5432)
            .withUsername("username")
            .withPassword("password")
            .withDatabaseName("database")
        init {
            psqlContainer.start()
            val mockEnv = mockk<Environment>(relaxed = true)
            every { mockEnv.databaseUsername } returns "username"
            every { mockEnv.databasePassword } returns "password"
            every { mockEnv.jdbcUrl() } returns psqlContainer.jdbcUrl
            database = Database(mockEnv, ApplicationState(true, true))
        }
        fun getNarmesteleder(): List<NarmestelederDbModel> {
            return database.connection.use {
                it.prepareStatement(
                    """
                    select * from narmesteleder order by last_update limit 100 ;
                """
                ).use { ps ->
                    ps.executeQuery().toNarmestelederDb()
                }
            }
        }

        fun clearAllData() {
            return database.connection.use {
                it.prepareStatement(
                    """
                    delete from narmesteleder;
                """
                ).use { ps ->
                    ps.executeUpdate()
                }
                it.commit()
            }
        }
    }
}
