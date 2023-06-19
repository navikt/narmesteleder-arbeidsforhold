package no.nav.syfo.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "narmesteleder_arbeidsforhold"

val HTTP_HISTOGRAM: Histogram =
    Histogram.Builder()
        .labelNames("path")
        .namespace(METRICS_NS)
        .name("requests_duration_seconds")
        .help("http requests durations for incoming requests in seconds")
        .register()

val CHECKED_NL_COUNTER =
    Counter.Builder()
        .labelNames("status")
        .name("check")
        .namespace(METRICS_NS)
        .help("Counts checked NL relations")
        .register()

val NL_TOPIC_COUNTER =
    Counter.build()
        .labelNames("status")
        .name("topic_counter")
        .namespace(METRICS_NS)
        .help("Counts messages from kafka (new or deleted)")
        .register()

val ERROR_COUNTER =
    Counter.build()
        .labelNames("error")
        .name("error")
        .namespace(METRICS_NS)
        .help("Error counters")
        .register()
