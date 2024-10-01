package au.org.ala.collectory

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.plugins.openapi.Path
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.opencsv.CSVWriter
import grails.converters.JSON
import grails.converters.XML
import groovy.json.JsonSlurper
import grails.web.http.HttpHeaders
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER
import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

/**
 * Request a scan and an update of a data provider that links to a GBIF IPT instance.
 */
class IptController {
    static final API_KEY_COOKIE = "ALA-API-Key"

    def collectoryAuthService
    def iptService
    def providerGroupService

    /**
     * Scan an IPT instance described by a data provider and provide a list of datasets that need to be updated.
     * Each dataset is mapped onto an ALA data resource; these can be created automatically during the scan.
     * The data provider uid must be provided. There are two additional optional parameters:
     * <dl>
     *    <dt>create (false)</dt>
     *    <dd> If set to true, then any previously unknown datasets have a data resource created and any existing datasets are updates.</dd>
     *    <dt>check (true)</dt>
     *    <dd>If set to true then only report data resources that need updating</dd>
     *    <dt>key (catalogNumber)</dt>
     *    <dd>The term that is used as a key when </dd>
     * <dt>
     * <p>
     * Authentication is done via the CollectoryWebServicesInterceptor
     * <p>
     * Output formats are JSON, XML or plain text (the default). Plain text is a list of updatable data resource ids
     * suitable for feeding into a shell script.
     */
    @Operation(
            method = "GET",
            tags = "ipt",
            operationId = "scanIpt",
            summary = "Scan an IPT instance described by a data provider id",
            description = "Scan an IPT instance described by a data provider id",
            requestBody = @RequestBody(
                    content = [
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ArrayList)
                            ),
                            @Content(
                                    mediaType = "test/xml",
                                    schema = @Schema(implementation = ArrayList)
                            ),
                            @Content(
                                    mediaType = "text/plain",
                                    schema = @Schema(implementation = ArrayList)
                            )
                    ]
            ),
            parameters = [
                    @Parameter(
                            name = "uid",
                            in = PATH,
                            description = "provider uid",
                            schema = @Schema(implementation = String),
                            example = "dp1518",
                            required = true
                    ),
                    @Parameter(
                            name = "create",
                            in = QUERY,
                            description = "Boolean flag to determine whether to update existing datasets and create data resources for new datasets",
                            schema = @Schema(implementation = Boolean),
                            example = "false",
                            required = false
                    ),
                    @Parameter(
                            name = "check",
                            in = QUERY,
                            description = "Boolean flag to  check to see ifresource needs updating by looking at the data currency",
                            schema = @Schema(implementation = Boolean),
                            example = "true",
                            required = false
                    ),
            ],
            responses = [
                    @ApiResponse(
                            description = "Result of the scan operation",
                            responseCode = "200",
                            content = [
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ArrayList)
                                    ),
                                    @Content(
                                            mediaType = "test/xml",
                                            schema = @Schema(implementation = ArrayList)
                                    ),
                                    @Content(
                                            mediaType = "text/plain",
                                            schema = @Schema(implementation = ArrayList)
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
    @Path("/ws/ipt/scan/{uid}")
    @Produces("text/plain")
    def scan() {
        def create = params.create != null && params.create.equalsIgnoreCase("true")
        def check = params.check == null || !params.check.equalsIgnoreCase("false")
        def keyName = params.key ?: 'occurrenceID'
        def isShareableWithGBIF = params.isShareableWithGBIF ? params.isShareableWithGBIF.toBoolean(): true
        def provider = providerGroupService._get(params.uid)

        def username = collectoryAuthService.username()
        def admin =  collectoryAuthService.userInRole(grailsApplication.config.ROLE_ADMIN)

        log.debug "Access by user ${username}, admin ${admin}"
        if (create && !admin) {
            render (status: 403, text: "Unable to create resources for " + params.uid)
            return
        }
        if (provider == null) {
            render (status: 400, text: "Unable to get data provider " + params.uid)
            return
        }
        try {
            def updates = provider == null ? null : iptService.scan(provider, create, check, keyName, username, admin, isShareableWithGBIF)
            log.info "${updates.size()} data resources to update for ${params.uid}"
            response.addHeader HttpHeaders.VARY, HttpHeaders.ACCEPT
            withFormat {
                text {
                    render updates.findAll({ dr -> dr.uid != null }).collect({ dr -> dr.uid }).join("\n")
                }
                xml {
                    render updates as XML
                }
                json {
                    render updates as JSON
                }
            }
        } catch (Exception e){
            log.error("Problem scanning IPT endpoint: " + e.getMessage(), e)
            render (status: 500, text: "Problem scanning data provider " + params.uid)
            return
        }
    }

    def syncReport(){

        response.setContentType("text/csv")
        response.setCharacterEncoding("UTF-8")
        response.setHeader("Content-disposition", "attachment;filename=ipt-sync.csv")

        def csvWriter = new CSVWriter(new OutputStreamWriter(response.outputStream))
        def provider = providerGroupService._get(params.uid)
        if(provider.websiteUrl) {
            def newMap = [:]
            DataResource.findAll().each { dr ->
                def idx = dr.name.toLowerCase().indexOf("- version")
                if (idx > 0) {
                    def searchedWith = dr.name.substring(0, idx).trim()
                    newMap.put(searchedWith, dr.uid)
                } else {
                    newMap.put(dr.name.toLowerCase(), dr.uid)
                }
            }

            def iptInventory = new JsonSlurper().parse(new URL( provider.websiteUrl + "/inventory/dataset"))
            def count = 0
            def iptMap = [:]

            String[] header = [
                    "EML URL",
                    "GUID",
                    "Title",
                    "Number of records in IPT",
                    "Number of records in Atlas",
                    "Atlas ID"
            ]
            csvWriter.writeNext(header)

            iptInventory.registeredResources.each { item ->
                iptMap.put(item.title, item.records)
                //retrieve UID, and do a count from the services
                def uid = newMap.get(item.title.toLowerCase())
                if(uid){
                    def jsonCount = new JsonSlurper().parse(new URL(grailsApplication.config.biocacheServicesUrl + "/occurrences/search?pageSize=0&fq=data_resource_uid:" + uid))
                    String[] row = [
                            item.eml,
                            item.gbifKey,
                            item.title,
                            item.records,
                            jsonCount.totalRecords,
                            uid
                    ]
                    csvWriter.writeNext(row)
                } else {String[] row = [item.eml, item.gbifKey, item.title, item.records, "0", "Not registered"]
                    csvWriter.writeNext(row)
                }
                count += 1
            }
            csvWriter.flush()
        }
    }
}
