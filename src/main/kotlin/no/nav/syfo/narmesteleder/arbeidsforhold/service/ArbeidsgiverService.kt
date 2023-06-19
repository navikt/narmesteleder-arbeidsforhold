package no.nav.syfo.narmesteleder.arbeidsforhold.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import no.nav.syfo.narmesteleder.arbeidsforhold.client.AccessTokenClient
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import no.nav.syfo.securelog

class ArbeidsgiverService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String
) {
    suspend fun getArbeidsgivere(fnr: String): List<Arbeidsgiverinfo> {
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4)
        securelog.info("Getting employers for fnr $fnr")
        val arbeidsgivere =
            arbeidsforholdClient.getArbeidsforhold(
                fnr = fnr,
                ansettelsesperiodeFom = ansettelsesperiodeFom,
                token = "Bearer ${accessTokenClient.getAccessToken(scope)}"
            )

        securelog.info(
            "arbeidsgivere for fnr $fnr: ${jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        }.writeValueAsString(arbeidsgivere)}"
        )

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        return arbeidsgivere
            .filter { it.arbeidsgiver.type == "Organisasjon" }
            .distinctBy { it.arbeidsgiver.organisasjonsnummer }
            .map { toArbeidsgiverInfo(it) }
    }

    private fun toArbeidsgiverInfo(arbeidsforhold: Arbeidsforhold): Arbeidsgiverinfo {
        return Arbeidsgiverinfo(
            orgnummer = arbeidsforhold.arbeidsgiver.organisasjonsnummer!!,
            tomDate = arbeidsforhold.ansettelsesperiode.periode.tom
        )
    }
}
