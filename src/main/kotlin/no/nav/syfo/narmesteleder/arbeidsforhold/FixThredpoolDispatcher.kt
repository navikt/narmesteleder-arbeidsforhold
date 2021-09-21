package no.nav.syfo.narmesteleder.arbeidsforhold

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

val Dispatchers.Fixed get() = FixThredpoolDispatcher.fixThredpoolDispatcher

class FixThredpoolDispatcher private constructor() : CoroutineDispatcher() {
    companion object {
        val fixThredpoolDispatcher = FixThredpoolDispatcher()
    }

    private val threadPool = Executors.newFixedThreadPool(2)
    private val dispatcher = threadPool.asCoroutineDispatcher()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatcher.dispatch(context, block)
    }
}
