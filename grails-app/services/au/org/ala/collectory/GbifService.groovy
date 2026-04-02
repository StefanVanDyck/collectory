package au.org.ala.collectory

import grails.converters.JSON
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.http.HttpEntity
import org.apache.http.HttpHeaders
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.tools.zip.ZipFile
import org.grails.web.json.JSONObject
import org.slf4j.LoggerFactory

import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.concurrent.Callable
import java.util.concurrent.Executors
/**
 * Services required to auto-load GBIF data into the collectory.
 *
 * @author Natasha Quimby (natasha.quimby@csiro.au)
 */
class GbifService {
    static final LOGGER = LoggerFactory.getLogger(GbifService.class)

    def grailsApplication
    def crudService
    def dataResourceFetchService

    static final String CITATION_FILE = "citations.txt"
    static final String RIGHTS_FILE = "rights.txt"
    static final String EML_DIRECTORY = "dataset"
    static final String OCCURRENCE_FILE = "occurrence.txt"
    static final String OCCURRENCE_DOWNLOAD = "occurrence/download/request" //POST request to this to start download
    // GET request to retrieve download
    static final String DOWNLOAD_STATUS = "occurrence/download/" //GET request to this
    static final String DATASET_RECORD_COUNT = "occurrence/count?datasetKey={0}"

    def CONCURRENT_LOADS = 3

    def pool = Executors.newFixedThreadPool(CONCURRENT_LOADS)
    def loading = false
    def loadMap = [:]
    def stopStatus = ["CANCELLED", "FAILED", "KILLED", "SUCCEEDED", "UNKNOWN"]

    /**
     * Returns the status information for a specific losd
     *
     * @param guid The load guid
     *
     * @return
     */
    def getStatusInfoFor(String country){
        return loadMap[country]
    }

    /**
     * performs the steps to create a new GBIF resource from the supplied
     * mulitpart file
     *
     * Supplied here so that a normal post webservice can call it as well as a view backed service.
     *
     * @param uploadedFile
     */
    def createGBIFResourceFromArchiveURL(String gbifFileUrl){

        //1) Save the file to the correct tmp staging location
        def fileId = System.currentTimeMillis()
        def tmpDir = new File(grailsApplication.config.uploadFilePath + File.separator + "tmp")
        if(!tmpDir.exists()){
            FileUtils.forceMkdir(tmpDir)
        }
        File localFile = new File(grailsApplication.config.uploadFilePath + File.separator + "tmp" + File.separator + fileId)

        //2) download the file
        def out = new BufferedOutputStream(new FileOutputStream(localFile))
        out << new URL(gbifFileUrl).openStream()
        out.close()

        //3) create the GBIF resource based on a local file now
        return createOrUpdateGBIFResource(localFile)
    }

    /**
     * Creates a data resource from the supplied file. Includes DWCA creation and property extraction.
     *
     * @param uploadedFile
     * @return
     */
    def createOrUpdateGBIFResource(File uploadedFile){
        //1) Extract the ZIP file
        //2) Extract the JSON for the data resource to create
        def json = extractDataResourceJSON(uploadedFile, uploadedFile.getParentFile());
        json['gbifDataset'] = true
        json['resourceType'] = 'records'
        json['contentTypes'] = (['point occurrence data', 'gbif import'] as JSON).toString()
        log.info("The JSON to create the dr : " + json)

        //3) Create or update the data resource
        def dr = dataResourceFetchService.findByGuidSafe(json.guid)
        if (!dr){
            dr = crudService.insertDataResource(json)
        } else {
            DataResource.withTransaction {
                crudService.updateDataResource(dr, json)
            }
        }

        log.info(dr.uid + "  " + dr.id + " " + dr.name)    //.toString() + " " + dr.hasErrors() + " " + dr.getErrors())

        //4) Create the DwCA for the resource using the GBIF default meta.xml and occurrences.txt
        String zipFileName = uploadedFile.getParentFile().getAbsolutePath() + File.separator + json.get("guid", "dwca") + ".zip"
        //add the occurrence.txt file
        File outFile = new File(zipFileName)

        uploadedFile.withInputStream { is ->
            outFile.withOutputStream { os ->
                IOUtils.copy(is, os)
                os.flush()
            }
        }
        log.info("Created the zip file " + zipFileName)
        //5) Upload the DwCA for the resource to the created data resource
        applyDwCA(outFile, dr.uid)
        return dr
    }

    /**
     * Adds the DWC-A to the data resource for use in loading
     * @param file a constructed archive to apply to the resource
     * @param dr  The data resource to apply the supplied archive to
     * @return
     */
    def applyDwCA(File file, String dataResourceUid){
        try {
            log.info("Copying DwCA to staging and associated the file to the data resource")
            def fileId = System.currentTimeMillis()
            String targetFileName = grailsApplication.config.uploadFilePath + fileId  + File.separator + file.getName()
            File targetFile = new File(targetFileName)
            FileUtils.forceMkdir(targetFile.getParentFile())
            file.renameTo(targetFile)
            log.info("Finished moving the file for " + dataResourceUid + " to " + targetFileName)
            DataResource.withTransaction {
                def dr = dataResourceFetchService.findByUidSafe(dataResourceUid)
                //move the DwCA where it needs to be
                def connParams = (new JsonSlurper()).parseText(dr.connectionParameters ?: '{}')
                connParams.url = 'file:///' + targetFileName
                connParams.protocol = "DwCA"
                connParams.termsForUniqueKey = ["gbifID"]
                //NQ we need a transaction so the this can be executed in a multi-threaded manner.
                dr.connectionParameters = (new JsonOutput()).toJson(connParams)
                dr.lastChecked = (new Date()).toTimestamp()
                log.info("Finished creating the connection params for " + dataResourceUid)
                dr.save(flush: true)
                log.info("Finished saving the connection params for " + dataResourceUid)
            }
        } catch (Exception e){
            log.error(e.getClass().toString() + " : " + e.getMessage(), e)
        }
    }

    /**
     * Extracts all the details from the GBIF download to use for the data resource.
     * @param zipFile
     * @param directoryForArchive
     * @return
     */
    def extractDataResourceJSON(File gbifArchiveFile, File directoryForArchive) {
        Map result = [:]

        ZipFile zipFile = new ZipFile(gbifArchiveFile)
        try {
            zipFile.getEntries().each { entry ->
                String name = entry.name
                switch (true) {
                    case name == CITATION_FILE:
                        zipFile.getInputStream(entry).withCloseable { is ->
                            result.citation = is.getText("UTF-8").replaceAll("\\n", " ")
                        }
                        break
                    case name == RIGHTS_FILE:
                        zipFile.getInputStream(entry).withCloseable { is ->
                            result.rights = is.getText("UTF-8").replaceAll("\\n", " ")
                        }
                        break
                    case name.startsWith(EML_DIRECTORY):
                        zipFile.getInputStream(entry).withCloseable { is ->
                            def xml = new XmlSlurper().parse(is)

                            result.guid = xml.@packageId?.toString()
                            result.pubDescription = xml.dataset?.abstract?.para?.toString()
                            result.name = xml.dataset?.title?.toString()

                            def contact = xml.dataset?.contact
                            result.phone = contact?.phone?.toString()
                            result.email = contact?.electronicMailAddress?.toString()

                            // Prefer explicit metadata if present
                            def gbifMeta = xml.additionalMetadata?.metadata?.gbif
                            if (gbifMeta) {
                                result.citation = gbifMeta.citation?.toString() ?: result.citation
                                result.rights = gbifMeta.rights?.toString() ?: result.rights
                            }
                        }
                        break

                    case name == OCCURRENCE_FILE:
                        File outputFile = new File(directoryForArchive, OCCURRENCE_FILE)

                        zipFile.getInputStream(entry).withCloseable { is ->
                            outputFile.withOutputStream { os ->
                                IOUtils.copy(is, os)
                            }
                        }
                        break
                }
            }
        } finally {
            zipFile.close()
        }

        return new JSONObject(result)
    }

    /**
     * Retrieves the status of the supplied GBIF download
     *
     * The possible status include:
     * CANCELLED
     * FAILED
     * KILLED
     * PREPARING
     * RUNNING
     * SUCCEEDED
     * SUSPENDED
     *
     * return "SUCCEEDED" when finished.
     *
     * @param downloadId
     */
    def getDownloadStatus(String downloadId, String userName, String password){
        def statusUrl = grailsApplication.config.gbifApiUrl + DOWNLOAD_STATUS + downloadId
        def json = getJSONWSWithAuth(statusUrl, userName, password)
        log.info("Download status for ${downloadId} : ${json?.status}")
        return json && json?.status ? json.status : "UNKNOWN"
    }

    /**
     * Retrieves the status of the supplied GBIF download
     *
     * The possible status include:
     * CANCELLED
     * FAILED
     * KILLED
     * PREPARING
     * RUNNING
     * SUCCEEDED
     * SUSPENDED
     *
     * return "SUCCEEDED" when finished.
     *
     * @param downloadId
     */
    def getDownloadStatus(String downloadId){
        getDownloadStatus(downloadId, grailsApplication.config.gbifApiUser, grailsApplication.config.gbifApiPassword)
    }

    /**
     * Uses a HTTP "GET" to return the JSON output of the supplied url with authentication
     * @param url
     * @param userName
     * @param password
     * @return
     */
    def getJSONWSWithAuth(String url, String username, String password) {

        log.info("Checking download status:" + url)
        HttpClient httpClient = HttpClientBuilder.create()
                .build();

        HttpGet httpGet = new HttpGet(url)
        if (username && password) {
            String encoding = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes())
            httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
        }

        HttpResponse response = httpClient.execute(httpGet)

        log.info("Response code " + response.getStatusLine().getStatusCode())
        if (response.getStatusLine().getStatusCode() == 200){
            ByteArrayOutputStream bos = new ByteArrayOutputStream()
            response.getEntity().writeTo(bos)
            String respText = bos.toString();
            JsonSlurper slurper = new JsonSlurper()
            return slurper.parseText(respText)
        } else {
            return null
        }
    }

    /**
     * Uses a HTTP "GET" to return the JSON output of the supplied url without authentication
     * @param url
     * @return
     */
    def getJSONWS(String url){
        return getJSONWSWithAuth(url, null, null)
    }

    /**
     * Check to see if data is available for the supplied resource ID.
     * @param resourceId
     * @return
     */
    def isDataAvailableForResource(String resourceId){
        String url = grailsApplication.config.gbifApiUrl + MessageFormat.format(DATASET_RECORD_COUNT, resourceId)
        try {
            def value = new URL(url).getText("UTF-8")
            if(value && value.toInteger() > 0){
                true
            } else {
                false
            }
        } catch (Exception e){
            log.error("Problem calling the dataset count service for ${resourceId}", e)
            false
        }
    }

    /**
     * Starts the GBIF download by calling the API/
     *
     * @param resourceId The GBIF identifier for the resource
     * @param username The username of a register GBIF user - a download will only be started when a valid user is supplied
     * @param email NOT USED as the email is automatically associated via the username
     * @param password  The password for the GBIF user.
     * @return The downloadId used to monitor when the download has been completed
     */
    def String startGBIFDownload(String resourceId, String repatCountryPolygon){
      startGBIFDownload(resourceId, repatCountryPolygon, new URL(grailsApplication.config.gbifApiUrl), grailsApplication.config.gbifApiUser, grailsApplication.config.gbifApiPassword)
    }

    def String startGBIFDownloadForRegion(String resourceId, String regionPolygon){
        startGBIFDownloadForRegion(resourceId, regionPolygon, new URL(grailsApplication.config.gbifApiUrl), grailsApplication.config.gbifApiUser, grailsApplication.config.gbifApiPassword)
    }

    /**
     * Starts the GBIF download by calling the API/
     *
     * @param resourceId The GBIF identifier for the resource
     * @param repatCountryPolygon The polygon of the country to repatriate for in WKT format. This will be used to limit the download to the specified country. If not supplied then the whole dataset will be downloaded.
     * @param username The username of a register GBIF user - a download will only be started when a valid user is supplied
     * @param email NOT USED as the email is automatically associated via the username
     * @param password  The password for the GBIF user.
     * @return The downloadId used to monitor when the download has been completed
     */
    static String startGBIFDownload(String resourceId, String repatCountryPolygon, URL endpointUrl, String username, String password){
        try {
            LOGGER.debug("[startGBIFDownload] Initialising download..... ")
            def params = [:]

            if (repatCountryPolygon){
                params = [
                        creator: username,
                        notification_address: [],
                        format: "DWCA",
                        predicate: [
                            type: "and",
                            predicates: [
                                [
                                    type : "equals",
                                    key  : "DATASET_KEY",
                                    value: resourceId
                                ],
                                [
                                    type : "within",
                                    geometry: repatCountryPolygon
                                ]
                            ]
                        ]
                ]
            } else {
                params = [
                        creator             : username,
                        notification_address: [],
                        format: "DWCA",
                        predicate           : [
                            type : "equals",
                            key  : "DATASET_KEY",
                            value: resourceId
                        ]
                ]
            }

            String downloadId = downloadFromGBIF(params, endpointUrl, username, password)
            downloadId
        } catch (Exception e){
            LOGGER.error(e.getMessage(), e)
            null
        }
    }


    /**
     * Starts the GBIF download by calling the API/
     *
     * @param resourceId The GBIF identifier for the resource
     * @param repatRegionPolygon The polygon of the region to repatriate for in WKT format. This will be used to limit the download to the specified region. If not supplied then the whole dataset will be downloaded.
     * @param username The username of a register GBIF user - a download will only be started when a valid user is supplied
     * @param password  The password for the GBIF user.
     * @return The downloadId used to monitor when the download has been completed
     */
    static String startGBIFDownloadForRegion(String resourceId, String repatRegionPolygon, URL endpointUrl, String username, String password){
        try {
            LOGGER.debug("[startGBIFDownload] Initialising download..... ")
            def params = [:]

            if (repatRegionPolygon){
                params = [
                        creator: username,
                        notification_address: [],
                        format: "DWCA",
                        predicate: [
                                type: "and",
                                predicates: [
                                        [
                                                type : "equals",
                                                key  : "DATASET_KEY",
                                                value: resourceId
                                        ],
                                        [
                                                type : "within",
                                                geometry: repatRegionPolygon
                                        ]
                                ]
                        ]
                ]
            } else {
                params = [
                        creator             : username,
                        notification_address: [],
                        format: "DWCA",
                        predicate           : [
                                type : "equals",
                                key  : "DATASET_KEY",
                                value: resourceId
                        ]
                ]
            }

            String downloadId = downloadFromGBIF(params, endpointUrl, username, password)
            downloadId
        } catch (Exception e){
            LOGGER.error(e.getMessage(), e)
            null
        }
    }

    /**
     * Starts a download from GBIF returning the downloadId for tracking status.
     *
     * @param requestBody
     * @param endpointUrl
     * @param username
     * @param password
     * @return
     */
    static String downloadFromGBIF(Map requestBody, URL endpointUrl, String username, String password) throws Exception {
        StringEntity requestEntity = new StringEntity(
                JsonOutput.toJson(requestBody),
                "application/json",
                "UTF-8")

        String encoding = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes())
        HttpClient httpClient = HttpClientBuilder.create()
                .build();

        HttpPost httpPost = new HttpPost(new URL(endpointUrl, OCCURRENCE_DOWNLOAD).toURI())
        httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
        httpPost.setEntity(requestEntity);

        CloseableHttpResponse response = httpClient.execute(httpPost);
        HttpEntity entity = response.getEntity();

        if (response.getStatusLine().statusCode in [200,201,202]){
            return IOUtils.readLines(entity.getContent()).get(0)
        } else {
            null
        }
    }

    /**
     * Gets a dataset from gbif.org
     *
     * @param datasetKey    The gbif dataset key
     * @param user          The gbif.org username
     * @param password      The gbif.org password
     * @return
     */
    def downloadGbifDataset(String datasetKey, String repatriationCountry, String region){
        GBIFActiveLoad l = new GBIFActiveLoad()
        l.gbifResourceUid = datasetKey
        l.repatriationCountry = repatriationCountry
        l.region = region

        def reloadExisting = true
        log.info("Started Gbif Dataset")
        //check to see if a load is already running. We can only have one at a time
        if (!loading){
            log.info("Loading resources from GBIF: ")
            loading = true
            loadMap[datasetKey] = l

            //at this point we need to return to the user and perform remaining tasks asynchronously
            pool.submit(new Runnable(){
                void run(){

                    def defer = { c -> pool.submit(c as Callable) }

                        defer {
                            Boolean skipReload = false
                            def existingDataResource = dataResourceFetchService.findByGuidSafe(l.gbifResourceUid)
                            if (!reloadExisting){
                                log.info("Reload existing resources set to false. Checking for " + l.gbifResourceUid)
                                if (existingDataResource){
                                    skipReload = true
                                }
                            }

                            // is data available
                            if (!isDataAvailableForResource(l.gbifResourceUid)){
                                l.phase = "Data is currently not available for this resource through GBIF"
                                loading = false
                                l.setCompleted()
                                return null
                            }

                            if (skipReload){
                                l.phase = "Resource is already loaded. To reload check the reload existing resource checkbox"
                                loading = false
                                l.dataResourceUid = existingDataResource.uid
                                l.setCompleted()
                                return null

                            } else {

                                log.info("Submitting " + l + " to be processed")
                                //1) Start the download
                                def regionPolygonMap = getRegionPolygonMap()
                                String countryPolygon = regionPolygonMap.get(l.repatriationCountry)
                                String regionPolygon = regionPolygonMap.get(l.region)
                                String downloadId = l.region ? startGBIFDownloadForRegion(l.gbifResourceUid, regionPolygon) : startGBIFDownload(l.gbifResourceUid, countryPolygon)
                                if (downloadId) {
                                    l.downloadId = downloadId
                                    //2) Monitor the download
                                    l.phase = "Generating Download..."
                                    String status = ""
                                    while (!stopStatus.contains(status)) {
                                        //sleep for 30 seconds between checks.
                                        Thread.sleep(3000)
                                        status = getDownloadStatus(l.downloadId)
                                    }
                                    log.info("Download status: " + status)
                                    //3) if the status was "SUCCEEDED" then starts the download
                                    if (status == "SUCCEEDED") {
                                        l.phase = "Downloading..."
                                        File localTmpDir = new File(grailsApplication.config.uploadFilePath + File.separator + "tmp" + File.separator + l.downloadId)
                                        FileUtils.forceMkdir(localTmpDir)
                                        String tmpFileName = localTmpDir.getAbsolutePath() + File.separator + l.downloadId

                                        // https://github.com/AtlasOfLivingAustralia/collectory-plugin/issues/53
                                        InputStream instream = new URL(grailsApplication.config.gbifApiUrl + OCCURRENCE_DOWNLOAD +
                                                "/" + l.downloadId + ".zip").openStream();
                                        FileOutputStream outstream = new FileOutputStream(tmpFileName);
                                        try {
                                            IOUtils.copy(instream, outstream);
                                        } catch (Exception e) {
                                            l.phase = "Failed To download from GBIF."
                                            loading = false;
                                            return null;

                                        } finally {
                                            IOUtils.closeQuietly(instream);
                                            IOUtils.closeQuietly(outstream);
                                        }

                                        //4) Now create the data resource using the file downloaded
                                        l.phase = "Creating GBIF Data resource..."
                                        def dr = createOrUpdateGBIFResource(new File(tmpFileName))
                                        l.phase = "GBIF Data resource created..."
                                        if (dr && existingDataResource) {
                                            l.dataResourceUid = dr.uid
                                            l.phase = "Data Resource Updated"
                                        } else if(dr) {
                                            l.dataResourceUid = dr.uid
                                            l.phase = "Data Resource Created"
                                        } else {
                                            l.phase = "Data Resource Creation Failed."
                                        }
                                    } else {
                                        l.phase = "Download Failed: " + status
                                    }
                                    //Thread.sleep(5000)
                                    l.setCompleted()
                                    //l.dataResourceUid="dr123"
                                    //check to see if all the items have finished loading
                                    loading = false
                                } else {
                                    l.phase = "Failed. Please check your authentication credentials are valid."
                                    loading = false
                                    return null
                                }
                            }
                        }
                }
            })
            return l
        } else {
            loading = false
            return l
        }
    }

    /**
     * Returns the status information for the supplied datasetKey
     * @param datasetKey
     * @return
     *
     */
    def getDatasetKeyStatusInfoFor(String datasetKey){
        return loadMap[datasetKey]
    }

    def getCountryMap(){
        def isoCodeList = getJSONWS(grailsApplication.config.gbifApiUrl + "node/country")
        //intersect with iso names
        def isoMap = [:]
        this.class.classLoader.getResourceAsStream("isoCodes.csv").readLines().each{
            def codeAndName = it.split("\t")
            isoMap.put(codeAndName[0], codeAndName[1])
        }
        def pubMap = [:]
        isoCodeList.each {
            def name = isoMap.get(it)
            pubMap.put(it, name)
        }
        return pubMap.sort { it.value }
    }

    def getRegionMap() {
        def gadmMap = [:]
        def resource = this.class.classLoader.getResource("belgiumRegionCodes.csv")
        if (!resource) {
            throw new FileNotFoundException("Resource not found: belgiumRegionCodes.csv")
        }

        resource.openStream().withReader { reader ->
            reader.eachLine { line ->
                def codeAndName = line.split("\t")
                if (codeAndName.size() >= 2) {
                    gadmMap[codeAndName[0]] = codeAndName[1]
                }
            }
        }

        return gadmMap
    }

    def getRegionPolygonMap() {
        def polygonMap = [:]
        def resource = this.class.classLoader.getResource("belgiumRegionPolygonWKT.csv")
        if (!resource) {
            throw new FileNotFoundException("Resource not found: belgiumRegionPolygonWKT.csv")
        }

        resource.openStream().withReader { reader ->
            reader.eachLine { line ->
                def codeAndPolygon = line.split("\t")
                if (codeAndPolygon.size() >= 2) {
                    polygonMap[codeAndPolygon[0]] = codeAndPolygon[1]
                }
            }
        }
        return polygonMap
    }

    def Date getGbifDatasetLastUpdated(String guid){

        try {
            def json = new JsonSlurper().parse(new URL(grailsApplication.config.gbifApiUrl + "dataset/" + guid))
            //TODO check with GBIF this is the appropriate timestamp to use
            if (json.pubDate) {
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sssXXX").parse(json.pubDate)
            } else {
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sssXXX").parse(json.modified)
            }
        } catch (Exception e){
            // expected with a 404
            log.error("Unable to retrieve pubDate for GBIF guid: " + guid)
            null
        }
    }

}
