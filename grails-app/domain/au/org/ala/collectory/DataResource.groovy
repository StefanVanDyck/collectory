package au.org.ala.collectory

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.sql.Timestamp

class DataResource implements ProviderGroup, Serializable {

    static final String ENTITY_TYPE = 'DataResource'
    static final String ENTITY_PREFIX = 'dr'

    static auditable = [ignore: ['version','dateCreated','lastUpdated','userLastModified']]

    static mapping = {
        uid index:'uid_idx'
        pubShortDescription type: "text"
        pubDescription type: "text"
        techDescription type: "text"
        focus type: "text"
        taxonomyHints type: "text"
        notes type: "text"
        networkMembership type: "text"
        sort: 'name'
        rights type:'text'
        citation type:'text'
        defaultDarwinCoreValues type:'text'
        connectionParameters type:'text'
        imageMetadata type:'text'
        harvestingNotes type:'text'
        mobilisationNotes type:'text'
        dataGeneralizations type:'text'
        informationWithheld type:'text'
        permissionsDocument type:'text'
        pubDescription type: "text"
        techDescription type: "text"
        focus type: "text"
        taxonomyHints type: "text"
        notes type: "text"
        networkMembership type: "text"
        gbifDataset defaultValue: "false"
        isShareableWithGBIF defaultValue: "true"
        makeContactPublic defaultValue: "true"
        methodStepDescription type: "text"
        qualityControlDescription type: "text"
        geographicDescription type: "text"
        purpose type: "text"
        dataCollectionProtocolName type: "text"
        dataCollectionProtocolDoc type: "text"
        suitableFor type: "text"
        suitableForOtherDetail type: "text"

        consumerInstitutions joinTable:[name:"data_resource_institution", key:'data_resource_id' ]
        consumerCollections joinTable:[name:"data_resource_collection", key:'data_resource_id' ]
    }

    String rights
    String citation
    String licenseType = "other"
    String licenseVersion
    String resourceType = "records"
    String provenance
    String informationWithheld
    String dataGeneralizations
    String permissionsDocument      // location of the documentation of the right to use
    String permissionsDocumentType = "Other"  // type of document
    boolean riskAssessment = false  // has risk assessment been done (for Data Provider Agreements only)
    boolean filed = false           // has the document been filed (for Data Provider Agreements only)
    String status = "identified"    // integration status (of the integration of the resource into the atlas)
    String harvestingNotes          // may include which components (text, images, etc) can be harvested
    String mobilisationNotes        //
    int harvestFrequency = 0
    Timestamp lastChecked           // when the last check was made for new data
    Timestamp dataCurrency          // the date of production of the most recent data file
    String connectionParameters     // json string containing parameters based on a connection profile - DIGiR, TAPIR, etc
    String imageMetadata            // json string containing default dublin core values for any images associated with this resource
    String defaultDarwinCoreValues  // json string containing default values to use for missing DwC fields
    int downloadLimit = 0           // max number of records that can be included in a single download - 0 = no limit
    String contentTypes             // json array of type of content provided by the resource
    boolean publicArchiveAvailable = false  // true if a DwC archive is allowed to be downloaded
    Boolean gbifDataset = false     //indicates this dataset was downloaded from GBIF
    Boolean isShareableWithGBIF = true     //indicates this dataset is shareable with GBIF
    DataProvider dataProvider
    Institution institution         // optional link to the institution whose records are served by this resource
    Boolean makeContactPublic = true
    Boolean isPrivate = false
    String repatriationCountry
    String dataCollectionProtocolName
    String dataCollectionProtocolDoc
    String suitableFor
    String suitableForOtherDetail

    //Additional EML fields
    String purpose
    String geographicDescription
    String westBoundingCoordinate
    String eastBoundingCoordinate
    String northBoundingCoordinate
    String southBoundingCoordinate
    String beginDate
    String endDate
    String methodStepDescription
    String qualityControlDescription

    String gbifDoi

    static hasMany = [externalIdentifiers: ExternalIdentifier, consumerInstitutions: Institution, consumerCollections: Collection]

    String createdByID

    static constraints = {
        guid(nullable:true, maxSize:256)
        uid(blank:false, maxSize:20)
        name(blank:false, maxSize:1024)
        acronym(nullable:true, maxSize:45)
        pubShortDescription(nullable:true, maxSize:100)
        pubDescription(nullable:true)
        techDescription(nullable:true)
        focus(nullable:true)
        address(nullable:true)
        latitude(nullable:true)
        longitude(nullable:true)
        altitude(nullable:true)
        state(nullable:true, maxSize:45)
        websiteUrl(nullable:true, maxSize:256)
        logoRef(nullable:true)
        imageRef(nullable:true)
        email(nullable:true, maxSize:256)
        phone(nullable:true, maxSize:200)
        isALAPartner()
        notes(nullable:true)
        networkMembership(nullable:true, maxSize:256)
        attributions(nullable:true, maxSize:256)
        taxonomyHints(nullable:true)
        keywords(nullable:true)
        gbifRegistryKey(nullable:true, maxSize:36)
        rights(nullable:true)
        citation(nullable:true)
        licenseType(nullable:true, maxSize:45)
        licenseVersion(nullable:true, maxSize:45)
        resourceType(maxSize:255)
        provenance(nullable:true,maxSize:45)
        dataProvider(nullable:true)
        institution(nullable:true)
        dataGeneralizations(nullable:true)
        informationWithheld(nullable:true)
        permissionsDocument(nullable:true)
        permissionsDocumentType(nullable:true,)
        status(maxSize:45)
        harvestingNotes(nullable:true)
        mobilisationNotes(nullable:true)
        lastChecked(nullable:true)
        dataCurrency(nullable:true)
        connectionParameters(nullable:true)
        imageMetadata(nullable:true)
        defaultDarwinCoreValues(nullable:true)
        gbifDataset(nullable:false)
        isShareableWithGBIF(nullable:false)
        contentTypes(nullable:true, maxSize:2048)
        makeContactPublic(nullable:false)
        purpose(nullable:true)
        geographicDescription(nullable:true)
        westBoundingCoordinate(nullable:true)
        eastBoundingCoordinate(nullable:true)
        northBoundingCoordinate(nullable:true)
        southBoundingCoordinate(nullable:true)
        beginDate(nullable:true)
        endDate(nullable:true)
        methodStepDescription(nullable:true)
        qualityControlDescription(nullable:true)
        gbifDoi(nullable:true)
        isPrivate(nullable:true)
        repatriationCountry(nullable:true)
        dataCollectionProtocolName(nullable:true)
        dataCollectionProtocolDoc(nullable:true)
        suitableFor(nullable:true)
        suitableForOtherDetail(nullable:true)
        createdByID(nullable:true)
    }

    static transients =  ['creativeCommons']

    boolean canBeMapped() {
        return false;
    }

    Boolean isVerified(){

        if(defaultDarwinCoreValues){
            def js = new JsonSlurper()
            def values = js.parseText(defaultDarwinCoreValues)
            if(
               values.georeferenceVerificationStatus
               &&
               values.identificationVerificationStatus
               &&
               values.georeferenceVerificationStatus == "verified"
               &&
               values.identificationVerificationStatus == "verified"
            ){
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    def markAsVerified(){

        if (!defaultDarwinCoreValues){
            defaultDarwinCoreValues = "{}"
        }

        def js = new JsonSlurper()
        def values = js.parseText(defaultDarwinCoreValues)
        values.georeferenceVerificationStatus = "verified"
        values.identificationVerificationStatus = "verified"
        defaultDarwinCoreValues = JsonOutput.toJson(values)
        save(flush:true)
    }

    def markAsUnverified(){

        if (!defaultDarwinCoreValues){
            defaultDarwinCoreValues = "{}"
        }

        def js = new JsonSlurper()
        def values = js.parseText(defaultDarwinCoreValues)
        values.georeferenceVerificationStatus = ""
        values.identificationVerificationStatus = ""
        defaultDarwinCoreValues = JsonOutput.toJson(values)
        save(flush:true)
    }

    /**
     * True if this resource uses a CC license.
     * @return
     */
    boolean isCreativeCommons() {
        return licenseType.contains('CC')
    }

    /**
     * True if this resource provides records for any number of collections.
     * @return
     */
    boolean hasMappedCollections() {
        return consumerInstitutions || consumerCollections
    }

    /**
     * Return the provider's address if the resource does not have one. If dp has no address try related entities.
     * @return
     */
    @Override def resolveAddress() {
        ProviderGroup.super.resolveAddress() ?: dataProvider?.resolveAddress()
    }

    /**
     * Returns the entity that is responsible for creating this resource - the data provider if there is one.
     * @return
     */
    @Override def createdBy() {
        return dataProvider ? dataProvider.createdBy() : ProviderGroup.super.createdBy()
    }

    /**
     * Return the provider's logo if the resource does not have one.
     * @return
     */
    @Override def buildLogoUrl() {
        if (logoRef) {
            return ProviderGroup.super.buildLogoUrl()
        }
        else {
            return dataProvider?.buildLogoUrl()
        }
    }

    /**
     * Returns the best available primary contact.
     * @return
     */
    @Override
    ContactFor inheritPrimaryContact() {
        return getPrimaryContact() ?: dataProvider?.inheritPrimaryContact()
    }

    /**
     * Returns the best available primary contact that can be published.
     * @return
     */
    @Override
    ContactFor inheritPrimaryPublicContact() {
        return getPrimaryPublicContact() ?: dataProvider?.inheritPrimaryPublicContact()
    }

    @Override
    def parent() {
        return dataProvider
    }

    long dbId() {
        return id;
    }

    String entityType() {
        return ENTITY_TYPE;
    }

    String shortProviderName(int len) {
        return dataProvider?.name?.length() > len ? dataProvider.name[0..len] + ".." : dataProvider?.name
    }

    String shortProviderName() {
        return shortProviderName(30)
    }

    String getRegion() {
        def parsed = new JsonSlurper().parseText(connectionParameters)
        return parsed instanceof Map ? parsed.region : null
    }

}
