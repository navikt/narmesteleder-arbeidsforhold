package no.nav.syfo.narmesteleder.arbeidsforhold.service

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.narmesteleder.arbeidsforhold.client.ArbeidsforholdClient
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsgiverinfo
import java.time.LocalDate

@KtorExperimentalAPI
class ArbeidsgiverService(
    private val arbeidsforholdClient: ArbeidsforholdClient,
    private val stsOidcClient: StsOidcClient
) {
    suspend fun getArbeidsgivere(fnr: String): List<Arbeidsgiverinfo> {
        val stsToken = stsOidcClient.oidcToken()
        val ansettelsesperiodeFom = LocalDate.now().minusMonths(4)
        val arbeidsgivere = arbeidsforholdClient.getArbeidsforhold(
            fnr = fnr,
            ansettelsesperiodeFom = ansettelsesperiodeFom,
            token = "Bearer ${stsToken.access_token}",
        )

        if (arbeidsgivere.isEmpty()) {
            return emptyList()
        }
        return arbeidsgivere.filter {
            it.arbeidsgiver.type == "Organisasjon"
        }.sortedWith(
            compareByDescending(nullsLast()) {
                it.ansettelsesperiode.periode.tom
            }
        ).distinctBy {
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
