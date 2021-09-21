package no.nav.syfo.narmesteleder.db

import java.time.OffsetDateTime
import java.util.UUID

data class NarmestelederDbModel(
    val narmestelederId: UUID,
    val lastUpdated: OffsetDateTime,
    val fnr: String,
    val orgnummer: String
)
