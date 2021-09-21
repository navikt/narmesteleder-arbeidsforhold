package no.nav.syfo.narmesteleder.arbeidsforhold.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.syfo.narmesteleder.arbeidsforhold.model.Arbeidsforhold
import java.time.LocalDate

class ArbeidsforholdClient(private val httpClient: HttpClient, private val basePath: String, private val apiKey: String) {
    private val arbeidsforholdPath = "$basePath/aareg-services/api/v1/arbeidstaker/arbeidsforhold"
    private val navPersonident = "Nav-Personident"
    private val navConsumerToken = "Nav-Consumer-Token"
    private val ansettelsesperiodeFomQueryParam = "ansettelsesperiodeFom"
    private val sporingsinformasjon = "sporingsinformasjon"

    suspend fun getArbeidsforhold(fnr: String, ansettelsesperiodeFom: LocalDate, token: String): List<Arbeidsforhold> {
        return httpClient.get<List<Arbeidsforhold>>(
            "$arbeidsforholdPath?" +
                "$ansettelsesperiodeFomQueryParam=$ansettelsesperiodeFom&" +
                "$sporingsinformasjon=false"
        ) {
            header("x-nav-apikey", apiKey)
            header(navPersonident, fnr)
            header(HttpHeaders.Authorization, token)
            header(navConsumerToken, token)
        }
    }
}
