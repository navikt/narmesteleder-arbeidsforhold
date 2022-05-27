package no.nav.syfo.application

import io.ktor.server.engine.ApplicationEngine
import java.util.concurrent.TimeUnit

class ApplicationServer(private val applicationServer: ApplicationEngine, private val applicationState: ApplicationState) {
    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                applicationState.ready = false
                applicationState.alive = false
                this.applicationServer.stop(TimeUnit.SECONDS.toMillis(30), TimeUnit.SECONDS.toMillis(30))
            }
        )
    }

    fun start() {
        applicationServer.start(true)
    }
}
