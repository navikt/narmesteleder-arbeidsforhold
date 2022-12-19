package no.nav.syfo.narmesteleder.arbeidsforhold.service

import no.nav.syfo.narmesteleder.arbeidsforhold.client.AccessTokenClient
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import java.time.LocalDate

class ArbeidsgiverService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String
) {
    suspend fun getArbeidsgivere(fnr: String): List<Arbeidsgiverinfo> {
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4)
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(
            fnr = fnr,
            ansettelsesperiodeFom = ansettelsesperiodeFom,
            token = "Bearer ${accessTokenClient.getAccessToken(scope)}"
        )

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        return arbeidsgivere.filter {
            it.arbeidsgiver.type == "Organisasjon"
        }.distinctBy {
            it.arbeidsgiver.organisasjonsnummer
        }.map {
            toArbeidsgiverInfo(it)
        }
    }

    private fun toArbeidsgiverInfo(
        arbeidsforhold: Arbeidsforhold
    ): Arbeidsgiverinfo {
        return Arbeidsgiverinfo(
            orgnummer = arbeidsforhold.arbeidsgiver.organisasjonsnummer!!,
            tomDate = arbeidsforhold.ansettelsesperiode.periode.tom
        )
    }
}
