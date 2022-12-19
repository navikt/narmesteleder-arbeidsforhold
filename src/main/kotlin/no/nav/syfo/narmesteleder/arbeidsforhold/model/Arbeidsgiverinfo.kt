package no.nav.syfo.narmesteleder.arbeidsforhold.model

import java.time.LocalDate

data class Arbeidsgiverinfo(
    val orgnummer: String,
    val tomDate: LocalDate?
)
