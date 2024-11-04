package no.nav.syfo.application

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.util.concurrent.TimeUnit

class ApplicationServer(
    private val applicationServer:
        EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>,
    private val applicationState: ApplicationState
) {
    init {
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    applicationState.ready = false
                    applicationState.alive = false
                    this.applicationServer.stop(
                        TimeUnit.SECONDS.toMillis(30),
                        TimeUnit.SECONDS.toMillis(30)
                    )
                }
            )
    }

    fun start() {
        applicationServer.start(true)
    }
}
