package au.org.ala.collectory.resources.gbif

import au.org.ala.collectory.DataProvider
import au.org.ala.collectory.DataResource
import au.org.ala.collectory.DataSourceConfiguration
import au.org.ala.collectory.ExternalResourceBean
import au.org.ala.collectory.exception.ExternalResourceException
import au.org.ala.collectory.resources.DataSourceAdapter
import au.org.ala.collectory.resources.TaskPhase
import au.org.ala.collectory.GbifService
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.commons.io.FileUtils
import org.grails.web.json.JSONObject
import org.slf4j.LoggerFactory

import java.text.DateFormat
import java.text.MessageFormat
import java.text.ParseException
import java.text.SimpleDateFormat
/**
 * Data source adapters for the GBIF API
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 *
 * @copyright Copyright (c) 2017 CSIRO
 */
class GbifDataSourceAdapter extends DataSourceAdapter {
    static final LOGGER = LoggerFactory.getLogger(GbifDataSourceAdapter.class)
    static final SOURCE = "GBIF"
    static final MessageFormat DATASET_SEARCH = new MessageFormat("dataset/search?publishingCountry={0}&offset={1}&limit={2}")
    static final MessageFormat DATASET_SEARCH_PROV = new MessageFormat("dataset/search?publishingCountry={0}&offset={1}&limit={2}&publishingOrg={3}")
    static final MessageFormat DATASET_GET = new MessageFormat("dataset/{0}")
    static final MessageFormat DATASET_RECORD_COUNT = new MessageFormat("occurrence/count?datasetKey={0}")
    static final MessageFormat DOWNLOAD_STATUS = new MessageFormat("occurrence/download/{0}")
    static final DateFormat TIMESTAMP_FORMAT= new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    static LICENSE_MAP = [
            "https://creativecommons.org/publicdomain/zero/1.0/legalcode": [licenseType: "CC0", licenseVersion: "1.0" ],
            "https://creativecommons.org/licenses/by-nc/4.0/legalcode":    [licenseType: "CC BY-NC", licenseVersion: "4.0" ],
            "https://creativecommons.org/licenses/by/4.0/legalcode":       [licenseType: "CC BY", licenseVersion: "4.0" ],
            "http://creativecommons.org/publicdomain/zero/1.0/legalcode":  [licenseType: "CC0", licenseVersion: "1.0" ],
            "http://creativecommons.org/licenses/by-nc/4.0/legalcode":     [licenseType: "CC BY-NC", licenseVersion: "4.0" ],
            "http://creativecommons.org/licenses/by/4.0/legalcode":        [licenseType: "CC BY", licenseVersion: "4.0" ]
    ]
    static TYPE_MAP = [
            "CHECKLIST"    : "species-list",
            "METADATA"     : "document",
            "OCCURRENCE"   : "records",
            "SAPLING_EVENT": "records"
    ]
    static DATASET_TYPES = [
            "OCCURRENCE"   : "Occurrence Records"  // We only allow occurrence records at the moment
    ]
    static CONTENT_MAP = [
            "CHECKLIST"    : ["species list", "taxonomy", "gbif import"],
            "METADATA"     : ["gbif import"],
            "OCCURRENCE"   : ["point occurrence data", "gbif import"],
            "SAPLING_EVENT": ["point occurrence data", "gbif import"]
    ]
    static DOWNLOAD_STATUS_MAP = [
            "CANCELLED" : TaskPhase.CANCELLED,
            "FAILED"    : TaskPhase.ERROR,
            "KILLED"    : TaskPhase.CANCELLED,
            "PREPARING" : TaskPhase.GENERATING,
            "RUNNING"   : TaskPhase.GENERATING,
            "SUCCEEDED" : TaskPhase.COMPLETED,
            "SUSPENDED" : TaskPhase.GENERATING,
            "UNKNOWN"   : TaskPhase.ERROR
    ]

    int pageSize = 500

    def gbifService

    GbifDataSourceAdapter(DataSourceConfiguration configuration) {
        super(configuration)
     }

    /**
     * Get the source label for external identifiers
     *
     * @return {@link #SOURCE}
     */
    @Override
    String getSource() {
        return SOURCE
    }

    @Override
    Map getCountryMap(){
        return gbifService.getCountryMap()
    }

    @Override
    Map getDatasetTypeMap() {
        return DATASET_TYPES
    }

    @Override
    List<Map> datasets() throws ExternalResourceException {
        int offset = 0
        def keys = []
        def datasets = []
        boolean atEnd = false
        Integer pageSizeToUse = pageSize
        if (configuration.maxNoOfDatasets < pageSize){
            pageSizeToUse = configuration.maxNoOfDatasets
        } else {
            pageSizeToUse = pageSize
        }

        while (!atEnd) {
            getLOGGER().info("Requesting dataset lists configuration.country: ${configuration.country} offset: ${offset}, pageSize: ${pageSizeToUse}, dataProvider: ${configuration.dataProviderUid}")

            JSONObject json = getJSONWS(buildSearchUrl(offset, pageSizeToUse))

            if (json?.results) {
                json.results.each {
                    keys << it.key
                    datasets << translate(it)
                }
            }
            offset += pageSize
            getLOGGER().info("Results: " + json.results.size())
            if(json.results.size() > 0){
                getLOGGER().info("Results: " + json.results[0].title)
            }
            atEnd = !json || !json.results || json.results.size() == 0 || json.endOfRecords.toBoolean() || offset > configuration.maxNoOfDatasets
        }

        getLOGGER().info("Total datasets retrieved: " + datasets.size())
        return datasets
    }

    private String buildSearchUrl(int offset, int pageSizeToUse) {
        StringBuilder sb = new StringBuilder()

        def optionalProvider
        if (configuration.dataProviderUid) {
            optionalProvider = DataProvider.findByUid(configuration.dataProviderUid)
        }   
        String url = optionalProvider == null ?
                DATASET_SEARCH.format([configuration.country, offset.toString(), pageSizeToUse.toString()].toArray()) :
                DATASET_SEARCH_PROV.format([configuration.country, offset.toString(), pageSizeToUse.toString(), optionalProvider.gbifRegistryKey].toArray())
        sb.append(url)
        if (configuration.name) {
            sb.append("&q=title:*${configuration.name}*")
        }
        sb.toString()
    }

    @Override
    ExternalResourceBean createExternalResource(Map external) {
        ExternalResourceBean ext = new ExternalResourceBean(name: external.name, guid: external.guid, source: external.source, sourceUpdated: external.dataCurrency)
        DataResource dr = ext.resolve(source)
        if (!dr) {
            ext.status = ExternalResourceBean.ResourceStatus.NEW
            ext.addResource = true
            ext.updateMetadata = false
            ext.updateConnection = true
        } else {
            ext.uid = dr.uid
            ext.existingChecked = dr.lastChecked ? new Date(dr.lastChecked.time) : null
            if (!dr.gbifDataset) {
                ext.status = ExternalResourceBean.ResourceStatus.LOCAL
                ext.addResource = false
                ext.updateMetadata = false
                ext.updateConnection = false
            }  else if (ext.existingChecked == null || ext.sourceUpdated == null || ext.existingChecked.before(ext.sourceUpdated)) {
                ext.status = ExternalResourceBean.ResourceStatus.CHANGED
                ext.addResource = false
                ext.updateMetadata = true
                ext.updateConnection = true
            } else {
                ext.status = ExternalResourceBean.ResourceStatus.UNCHANGED
                ext.addResource = false
                ext.updateMetadata = false
                ext.updateConnection = false
            }
        }
        return ext
     }

    @Override
    Map getDataset(String id) throws ExternalResourceException {
        JSONObject json = getJSONWS(DATASET_GET.format([id ].toArray()), false)
        return json ? translate(json) : null
    }

    Map translate(dataset) {
        def currency = null
        def originator = dataset.contacts?.find { it.type == "ORIGINATOR" }
        def address = originator == null ? null : [
                street: originator.address?.join(" "),
                city: originator.city,
                state: originator.province,
                postcode: originator.postalCode,
                country: originator.country
        ]
        def phone = originator?.phone ? originator.phone.first() : null
        def email = originator?.email ? originator?.email?.first() : null
        def license = LICENSE_MAP[dataset.license]
        def recordType = TYPE_MAP[dataset.type]
        def contentTypes = CONTENT_MAP[dataset.type] ?: []
        def source = dataset.doi?.replace("doi:", "https://doi.org/")
        try {
            currency = dataset.pubDate ? TIMESTAMP_FORMAT.clone().parse(dataset.pubDate) : null
        } catch (ParseException ex) {
        }
        def resource = [
                name: dataset.title,
                acronym: dataset.abbreviation,
                guid: dataset.key,
                address: address,
                phone: phone,
                email: email,
                pubDescription: dataset.description,
                state: address?.state,
                websiteUrl: dataset.homepage,
                rights: dataset.rights,
                licenseType: license?.licenseType,
                licenseVersion: license?.licenseVersion,
                citation: dataset.citation?.identifier ? dataset.citation.identifier : dataset.citation?.text,
                resourceType: recordType,
                contentTypes: JsonOutput.toJson(contentTypes),
                dataCurrency: currency,
                lastChecked: new Date(),
                source: source,
                gbifDoi: dataset.doi,
                gbifRegistryKey: dataset.key,
                gbifDataset: true,
                isShareableWithGBIF: false
        ]
        addDefaultDatasetValues(resource)
        return resource
    }

    /**
     * Uses a HTTP "GET" to return the JSON output of the supplied url
     *
     * @param url The request URL, relative to the configuration endpoint
     *
     * @return A JSON response
     */
    def getJSONWS(String path, boolean authRequired = true) throws ExternalResourceException {

        def url = new URL(configuration.endpoint, path)
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod("GET")

        // Add HTTP Basic Authentication header
        if (authRequired) {
            String authString = "${configuration.username}:${configuration.password}"
            String encodedAuthString = "Basic " + authString.bytes.encodeBase64().toString()
            connection.setRequestProperty("Authorization", encodedAuthString)
        }

        // Check for a successful response code (200 OK)
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            def response = new StringBuffer()
            connection.inputStream.withReader { reader ->
                response << reader.getText()
            }
            // Parse JSON response
            return new JsonSlurper().parseText(response.toString())
        } else {
            // Handle error response (if needed)
            throw new ExternalResourceException("Unable to get ${url.toString()} response ${connection.responseCode }", "manage.note.note10", url.toString(), connection.responseCode )
        }
    }

    /**
     * Check to see if data is available for the supplied resource ID.
     * @param guid The resource GUID
     *
     * @return True if there is data available
     */
    @Override
    boolean isDataAvailableForResource(String guid) throws ExternalResourceException {
        def url = new URL(configuration.endpoint, DATASET_RECORD_COUNT.format([guid].toArray()))
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()
        connection.setRequestMethod("GET")

        String authString = "${configuration.username}:${configuration.password}"
        String encodedAuthString = "Basic " + authString.bytes.encodeBase64().toString()
        connection.setRequestProperty("Authorization", encodedAuthString)

        // Check for a successful response code (200 OK)
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            def response = new StringBuffer()
            connection.inputStream.withReader { reader ->
                response << reader.getText()
            }
            // Parse response
            return response && response.length() > 0 && response.toInteger() > 0
        } else {
            // Handle error response (if needed)
            throw new ExternalResourceException("Unable check for data", "manage.note.note11", guid, connection.responseCode)
        }
    }

    /**
     * Collect the generated data from the GBIF server
     *
     * @param id The download id
     * @param target The target file
     */
    @Override
    void downloadData(String id, File target) throws ExternalResourceException {
        def status = getJSONWS(DOWNLOAD_STATUS.format([id].toArray()))
        if (DOWNLOAD_STATUS_MAP[status?.status] != TaskPhase.COMPLETED) {
            throw new ExternalResourceException("Expecting completed generation", "manage.note.note07", status.status)
        }
        def downloadUrl = new URL(status.downloadLink)
        Authenticator.setDefault (new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication (configuration.username, configuration.password.toCharArray());
            }
        });
        target.withOutputStream { out ->
            def url = downloadUrl.openConnection()
            out << url.inputStream
        }
    }

    /**
     * Starts the GBIF download by calling the API.
     * <p>
     * The GBIF API is difficult at this point. It returns a text string containing a string download Id
     * but claims that the content type is application/json
     *
     * @param guid The GBIF identifier for the resource
     * @param country The country code
     * @return The downloadId used to monitor when the download has been completed
     */
    @Override
    String generateData(String guid, String country) throws ExternalResourceException {
        GbifService.startGBIFDownload(guid, country, configuration.endpoint, configuration.username, configuration.password)
    }

    /**
     * Starts the GBIF download by calling the API.
     * @param guid The GBIF identifier for the resource
     * @param region The gadm.org code of the region (1st level)
     * @return
     * @throws ExternalResourceException
     */
    @Override
    String generateDataForRegion(String guid, String region) throws ExternalResourceException {
        GbifService.startGBIFDownloadForRegion(guid, region, configuration.endpoint, configuration.username, configuration.password)
    }
/**
     * Check to see how the download is coming along
     *
     * @param id The download id
     *
     * @return Either completed, generating or an error status
     */
    @Override
    TaskPhase generateStatus(String id) throws ExternalResourceException {
        def status = getJSONWS(DOWNLOAD_STATUS.format([id].toArray()))
        LOGGER.debug("Download status for ${id}: ${status}")
        DOWNLOAD_STATUS_MAP[status?.status] ?: TaskPhase.GENERATING
    }

    /**
     * Processing simply involves moving the DwCA into the work directory
     *
     * @param downloaded The downloaded data
     * @param workDir The work directory
     * @param resource The external resource description
     *
     * @return The resulting processed file
     */
    @Override
    File processData(File downloaded, File workDir, ExternalResourceBean resource) throws ExternalResourceException {
        try {
            File upload = new File(workDir, resource.downloadId + "-dwca.zip")
            FileUtils.moveFile(downloaded, upload)
            return upload
        } catch (IOException ex) {
            throw new ExternalResourceException("Unable to process download", ex, "manage.note.note09", downloaded)
        }
    }

    /**
     * Return a DataResource update with suitable connection parameters, etc.
     *
     * @param upload The file to upload
     * @param connection The existing connection parameters
     * @param resource The external resource
     *
     * @return A suitable update
     */
    @Override
    Object buildConnection(File upload, Object connection, ExternalResourceBean resource) throws ExternalResourceException {
        def update = [:]
        connection.url = "file:///${upload.absolutePath}"
        connection.protocol = "DwCA"
        connection.termsForUniqueKey = ["http://rs.gbif.org/terms/1.0/gbifID"]
        update.connectionParameters = (new JsonOutput()).toJson(connection)
        return update
    }
}
