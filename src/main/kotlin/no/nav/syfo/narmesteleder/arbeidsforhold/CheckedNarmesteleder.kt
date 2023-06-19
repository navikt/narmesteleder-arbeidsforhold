package no.nav.syfo.narmesteleder.arbeidsforhold

import no.nav.syfo.narmesteleder.db.NarmestelederDbModel

data class CheckedNarmesteleder(
    val narmestelederDbModel: NarmestelederDbModel,
    val valid: Boolean,
    val failed: Boolean
)
