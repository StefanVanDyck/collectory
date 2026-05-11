package au.org.ala.collectory

import au.org.ala.PermissionRequired
import au.org.ala.collectory.resources.gbif.GbifRepatDataSourceAdapter
import au.org.ala.plugins.openapi.Path
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import grails.converters.JSON
import groovy.json.JsonSlurper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import javax.ws.rs.Produces
import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH

class GbifController {
    def gbifRegistryService
    def asyncGbifRegistryService
    def gbifService
    def collectoryAuthService
    def externalDataService

    @PermissionRequired(roles = ['gbifRegistrationRole','ROLE_ADMIN'])
    def healthCheck() {
        gbifRegistryService.generateSyncBreakdown()
    }

    @PermissionRequired(roles = ['gbifRegistrationRole','ROLE_ADMIN'])
    def healthCheckLinked() {

        log.info("Starting report.....")
        def url = grailsApplication.config.biocacheServicesUrl + "/occurrences/search?q=*:*&facets=data_resource_uid&pageSize=0&facet=on&flimit=-1"
        def js = new JsonSlurper()
        def biocacheSearch = js.parse(new URL(url), "UTF-8")

        def dataResourcesWithData = [:]
        def shareable = [:]
        def licenceIssues = [:]
        def notShareable = [:]
        def providedByGBIF = [:]
        def linkedToDataProvider = [:]
        def linkedToInstitution = [:]

        biocacheSearch.facetResults[0].fieldResult.each { result ->
            def uid = result.fq.replaceAll("\"","").replaceAll("data_resource_uid:","")

            def isShareable = true

            //retrieve current licence
            def dataResource = DataResource.findByUid(uid)
            if(dataResource) {

                dataResourcesWithData[dataResource] = result.count

                //get the data provider if available...
                def linked = false

                if(dataResource.consumerInstitutions){
                    //we have an institution
                    linkedToInstitution[dataResource] = result.count
                    linked = true
                }

                if(!linked) {
                    def dataProvider = dataResource.getDataProvider()
                    if(!dataProvider){
                        isShareable = false //no institution and no data provider
                    } else {
                        linkedToDataProvider[dataResource] = result.count
                        linked = true
                    }
                }

                if(linked) {

                    if (dataResource.licenseType == null || !dataResource.licenseType.startsWith("CC")) {
                        licenceIssues[dataResource] = result.count
                        isShareable = false
                    }

                    if (!dataResource.isShareableWithGBIF) {
                        notShareable[dataResource] = result.count
                        isShareable = false
                    }

                    if (dataResource.gbifDataset) {
                        providedByGBIF[dataResource] = result.count
                        isShareable = false
                    }

                    if (isShareable) {
                        shareable[dataResource] = result.count
                    }
                }
            }
        }

        [
                dataResourcesWithData:dataResourcesWithData,
                shareable:shareable,
                licenceIssues:licenceIssues,
                notShareable:notShareable,
                providedByGBIF:providedByGBIF,
                linkedToDataProvider: linkedToDataProvider,
                linkedToInstitution: linkedToInstitution,
        ]

    }

    /**
     * Download CSV report of our ability to share resources with GBIF.
     *
     * @return
     */
    @PermissionRequired(roles = ['gbifRegistrationRole','ROLE_ADMIN'])
    def downloadCSV() {
        response.setContentType("text/csv")
        response.setHeader("Content-disposition", "attachment;filename=gbif-healthcheck.csv")
        gbifRegistryService.writeCSVReportForGBIF(response.outputStream)
    }

    def syncAllResources(){
        log.info("Starting all sync resources...checking user has role ${grailsApplication.config.gbifRegistrationRole}")
        def errorMessage = ""

        try {
            if (collectoryAuthService.isAuthorised([grailsApplication.config.gbifRegistrationRol] as String[])) {
                asyncGbifRegistryService.updateAllResources()
                        .onComplete {
                            log.info "Sync complete"
                        }
                        .onError { Throwable err ->
                            log.error("An error occured ${err.message}", err)
                        }
            } else {
                errorMessage = "User does not have sufficient privileges to perform this."

                log.error("Starting all sync resources..." + errorMessage)
            }
        } catch (Exception e){
            log.error(e.getMessage(), e)
        }

        [errorMessage: errorMessage]
    }

    @SecurityRequirement(name="JWT")
    @Operation(
            method = "GET",
            tags = "gbif",
            operationId = "scanGbif",
            summary = "Update the collectory with data from external resources GBIF",
            description = "Update the collectory with data from external resources i.e. GBIF",
            parameters = [
                    @Parameter(
                            name = "uid",
                            in = PATH,
                            description = "provider uid",
                            schema = @Schema(implementation = String),
                            example = "1",
                            required = true
                    ),
            ],
            responses = [
                    @ApiResponse(
                            description = "Result of the scan operation",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = GbifScanResponse)
                                    )
                            ],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string"))
                            ]
                    )
            ],
            security = [@SecurityRequirement(name = 'openIdConnect')]
    )
    @Path("/ws/gbif/scan/{uid}")
    @Produces("application/json")
    @PermissionRequired(roles = ['gbifRegistrationRole','ROLE_ADMIN'], scopes = ['REQUIRED_SCOPES'])
    def scan(){
        if (!params.uid || !params.uid.startsWith('dp')){
            response.sendError(400, "No valid UID supplied")
            return
        }

        DataProvider dataProvider = DataProvider.findByUid(params.uid)
        if (!dataProvider){
            response.sendError(404)
            return
        }

        def resources = dataProvider.resources
        def output = []
        def updates = []
        resources.each { DataResource resource ->
            Date lastUpdated = gbifService.getGbifDatasetLastUpdated(resource.guid)
            //get last updated data
            def resourceDescription =  [uid:resource.uid,
                         name: resource.name,
                         lastUpdated: resource.lastUpdated,
                         guid: resource.guid,
                         country: resource.repatriationCountry,
                         pubDate: lastUpdated,
                         inSync:  !(lastUpdated > resource.lastUpdated)
            ]
            output << resourceDescription
            if (lastUpdated > resource.lastUpdated) {
                updates << resourceDescription
            }
        }

        DataSourceConfiguration configuration = new DataSourceConfiguration()
        configuration.adaptorClass = GbifRepatDataSourceAdapter.class
        configuration.endpoint = new URL(grailsApplication.config.gbifApiUrl)
        configuration.username = grailsApplication.config.gbifApiUser
        configuration.password = grailsApplication.config.gbifApiPassword

        def externalResourceBeans = []

        output.each { res ->
            if (!res.inSync && res.guid){
                res.status = "RELOADING"
                externalResourceBeans << new ExternalResourceBean(
                        uid: res.uid, guid: res.guid, name: res.name, country: res.country, updateMetadata:true, updateConnection:true)
            } else {
                res.status = "IN_SYNC"
            }
        }
        configuration.resources = externalResourceBeans

        def loadGuid = UUID.randomUUID().toString()

        log.info("Reloading process ID " + loadGuid)
        externalDataService.updateFromExternalSources(configuration, loadGuid)

        def fullOutput =
                [loadGuid: loadGuid,
                 trackingUrl: createLink(controller:"manage", action:"externalLoadStatus", params: [loadGuid: loadGuid]),
                 updates: updates,
                 resources: output
                ]
        render(fullOutput as JSON)
    }

    @JsonIgnoreProperties('metaClass')
    class GbifScanResponse {
        String loadGuid
        String trackingUrl
        ArrayList<Object> updates
        ArrayList<Object>  resources

    }
}
