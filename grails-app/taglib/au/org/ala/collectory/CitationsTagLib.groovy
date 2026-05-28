package au.org.ala.collectory

import groovy.json.JsonSlurper
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.methods.HttpGet
import org.apache.http.util.EntityUtils

class CitationsTagLib {

    static namespace = 'citations'

    def gbifLink = { attrs ->
        def gbifUrl = """${grailsApplication.config.gbif.citations.lookup}${attrs.gbifRegistryKey}"""
        if (grailsApplication.config.gbif.citations.enabled.toBoolean()) {

            try {

                HttpClient client = HttpClientBuilder.create().build()

                HttpGet request = new HttpGet(gbifUrl)

                request.setHeader("User-Agent", "Mozilla/5.0")
                request.setHeader("Accept", "application/json")

                def response = client.execute(request)

                int statusCode = response.statusLine.statusCode

                String responseBody = response.entity ? EntityUtils.toString(response.entity, "UTF-8") : null

                if (statusCode >= 200 && statusCode < 300) {

                    def js = new JsonSlurper()
                    def data = js.parseText(responseBody)

                    if (data.count) {
                        out << """<a class="btn btn-default" href="${grailsApplication.config.gbif.citations.search}${attrs.gbifRegistryKey}">&nbsp;<span class="glyphicon glyphicon-bullhorn"></span>&nbsp; ${data.count} ${g.message(code:"citations.available", default:"citations for these data")}</a>"""
                    }

                } else {
                    log.error("Retrieving citation count from GBIF: unexpected response code ${statusCode}")
                    log.error("Retrieving citation count from GBIF: response body: ${responseBody}")
                }

            } catch (Exception e) {
                log.error("Problem retrieving citation count from GBIF: ${e.message}", e)
            }
        }
    }

    /**
     * Convert the gbifDoi value to a DOI URL (link)
     *
     * @attr gbifDoi REQUIRED the gbifDoi value
     */
    def doiLink = { attrs, body ->
        String gbifDoi = (attrs.gbifDoi as String).toLowerCase()
        String doiUrl

        if (gbifDoi.startsWith("https://doi")) {
            // properly formatted DOI
            doiUrl = gbifDoi
        } else if (gbifDoi.startsWith("doi")) {
            // Old GBIF DOI API which used a "doi:" prefix
            doiUrl = "https://${gbifDoi.replaceAll('doi:', 'doi.org/')}"
        } else {
            // New GBIF DOI API provides the DOI "path" only
            doiUrl = "https://doi.org/${gbifDoi}"
        }

        out << doiUrl
    }
}
