package au.org.ala.collectory

import au.org.ala.PermissionRequired
import grails.converters.JSON
import java.text.SimpleDateFormat
import au.org.ala.collectory.resources.PP
import au.org.ala.collectory.resources.DarwinCoreFields

@PermissionRequired(roles=['ROLE_EDITOR', 'ROLE_ADMIN'])
class DataResourceController extends ProviderGroupController {

    def metadataService, dataImportService, gbifRegistryService, authService, crudService

    DataResourceController() {
        entityName = "DataResource"
        entityNameLower = "dataResource"
    }

    def index = {
        redirect(action:"list")
    }


    def markAsVerified = {
        def instance = DataResource.findByUid(params.uid)
        if (instance){
            DataResource.withTransaction {
                instance.markAsVerified()
            }
        }
        redirect(action: 'show', params: [id:params.uid])
    }


    def markAsUnverified = {
        def instance =  DataResource.findByUid(params.uid)
        if (instance){
            DataResource.withTransaction {
                instance.markAsUnverified()
            }
        }
        redirect(action: 'show', params: [id:params.uid])
    }


    // list all entities
    def list = {
        if (params.message)
            flash.message = params.message
        params.max = Math.min(params.max ? params.int('max') : 100, 10000)
        params.sort = params.sort ?: "name"
        params.order = params.order ?: "asc"
        activityLogService.log username(), isAdmin(), Action.LIST

        if(params.resourceType){
            [instanceList: DataResource.findAllWhere(['resourceType' : params.resourceType], params), entityType: 'DataResource', instanceTotal: DataResource.count()]

        } else {
            [instanceList: DataResource.list(params), entityType: 'DataResource', instanceTotal: DataResource.count()]
        }
    }

    def show = {
        def instance = get(params.id)
        if (!instance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'dataResource.label', default: 'Data resource'), params.id])}"
            redirect(action: "list")
        }
        else {
            log.debug "Ala partner = " + instance.isALAPartner
            def suitableFor = providerGroupService.getSuitableFor()
            activityLogService.log username(), isAdmin(), instance.uid, Action.VIEW

            [instance: instance, contacts: instance.getContacts(), changes: getChanges(instance.uid), suitableFor: suitableFor]
        }
    }

    def editConsumers(){
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        } else {
            // are they allowed to edit
            if (collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN) || !grailsApplication.config.security.oidc.enabled.toBoolean()) {
                render(view: 'consumers', model:[command: pg, source: params.source])
            } else {
                render("You are not authorised to edit these properties.")
            }
        }
    }

    boolean isCollectionOrArray(object) {
        [Collection, Object[]].any { it.isAssignableFrom(object.getClass()) }
    }


    def updateImageMetadata(){

        def ignores = ["action", "version", "id", "format", "controller"]

        //get the UID
        def dataResource = get(params.id)
        def metadata = [:]
        DataResource.withTransaction {
            params.entrySet().each {
                if(!(it.key in ignores)){
                    metadata[it.key] = it.value
                }
            }
            dataResource.imageMetadata = (metadata as JSON).toString()
            dataResource.save(flush:true)
        }
        redirect(action: 'show', params: [id:params.id])
    }


    def updateContribution = {
        def pg = get(params.id)

        // process connection parameters
        def protocol = params.remove('protocol')
        def cp = [:]
        if (protocol) {
            cp.protocol = protocol
        }
        def profile = metadataService.getConnectionProfile(protocol)
        profile.params.each {pp ->
            if (pp.type == 'boolean') {
                // the presence of the param indicates it is checked
                cp."${pp.paramName}" = params.containsKey(pp.paramName)
            }
            else if (params."${pp.paramName}") {
                if (pp.paramName == 'termsForUniqueKey') {
                    cp."${pp.paramName}" = params."${pp.paramName}".tokenize(', ')
                } else if (pp.type == 'delimiter') {
                    def str = params."${pp.paramName}"
                    str = str.replaceAll('HT', PP.HT_CHAR)
                    str = str.replaceAll('LF', PP.LF_CHAR)
                    str = str.replaceAll('VT', PP.VT_CHAR)
                    str = str.replaceAll('FF', PP.FF_CHAR)
                    str = str.replaceAll('CR', PP.CR_CHAR)
                    cp."${pp.paramName}" = str
                } else if(pp.paramName == 'url') {
                    if(isCollectionOrArray(params.url)){
                        def normalised = []
                        params.url.each {
                           if(it.trim().length() > 0){
                             normalised << it.trim()
                           }
                        }
                        cp.url = normalised.toSet()
                    } else {
                        cp.url = params.url
                    }
                } else {
                    cp."${pp.paramName}" = params."${pp.paramName}"
                }
            }
        }
        params.connectionParameters = (cp as JSON).toString()

        // process dates
        def lastChecked = params.remove('lastChecked')
        if (lastChecked) {
            pg.lastChecked = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S").parse(lastChecked).toTimestamp()
        }
        def dataCurrency = params.remove('dataCurrency')
        if (dataCurrency) {
            pg.dataCurrency = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S").parse(dataCurrency).toTimestamp()
        }

        // process default DwC values
        def ddv = [:]
        DarwinCoreFields.fields.each {
            if (params[it.name]) {
                ddv[it.name] = params[it.name]
            }
            if (params.otherKey && params.otherValue) {
                ddv[params.otherKey] = params.otherValue
            }
        }
        params.defaultDarwinCoreValues = (ddv as JSON).toString()

        // update
        genericUpdate pg, 'contribution'
    }

    def updateRights = {
        def pg = get(params.id)
        //check licence
        if(params.licenceID){
            def selectedLicence = Licence.get(params.licenceID)
            params.licenseType = selectedLicence.acronym
            params.licenseVersion = selectedLicence.licenceVersion
        } else {
            params.licenseType = ""
            params.licenseVersion = ""
        }
        genericUpdate pg, 'rights'
    }

    def updateGBIFDetails = {
        String gbifRegion = params['repatriationCountry']
        boolean isSharableWithGBIF = params['isShareableWithGBIF'] == 'on'
        boolean isGBIFData = params['gbifDataset'] == 'on'

        def pg = get(params.id)
        pg.isShareableWithGBIF = isSharableWithGBIF
        pg.gbifDataset = isGBIFData
        pg.repatriationCountry = gbifRegion
        pg.userLastModified = collectoryAuthService?.username()

        /**
         * GBIF publishing country is set on the institution, not the data resource
         * @link GbifRegistryService.registerDataResource
         */
        if ( pg.institution ) {
            pg.institution.gbifCountryToAttribute = gbifRegion
        } else if ( pg.dataProvider ) {
            pg.dataProvider.gbifCountryToAttribute = gbifRegion
        }

        DataResource.withTransaction {
            pg.save(flush: true)
        }
        updateGBIF.call()
    }

    def registerGBIF = {
        def pg = get(params.id)
        if (authService.userInRole(grailsApplication.config.gbifRegistrationRole)) {
            log.info("[User ${authService.getUserId()}] has selected to register ${pg.uid} in GBIF...")
            Map result = gbifRegistryService.registerDataResource(pg)
            flash.message = result.message
            redirect(action: "show", id: params.uid ?: params.id)
        } else {
            flash.message = "User does not have sufficient privileges to perform this. ${grailsApplication.config.gbifRegistrationRole} role required"
            redirect(action: "show", id: params.uid ?: params.id)
        }
    }

    def updateGBIF = {
        def pg = get(params.id)
        if (authService.userInRole(grailsApplication.config.gbifRegistrationRole)) {
            log.info("[User ${authService.getUserId()}] has selected to update ${pg.uid} in GBIF...")
            Map result = gbifRegistryService.registerDataResource(pg)
            flash.message = result.message
            redirect(action: "show", id: params.uid ?: params.id)
        } else {
            flash.message = "User does not have sufficient privileges to perform this. ${grailsApplication.config.gbifRegistrationRole} role required"
            redirect(action: "show", id: params.uid ?: params.id)
        }
    }

    def deleteGBIF = {
        def pg = get(params.id)
        if (authService.userInRole(grailsApplication.config.gbifRegistrationRole)) {
            log.info("[User ${authService.getUserId()}] has selected to delete ${pg.uid} from GBIF...")
            gbifRegistryService.deleteDataResource(pg)
            flash.message = "Resource removed from GBIF"
            redirect(action: "show", id: params.uid ?: params.id)
        } else {
            flash.message = "User does not have sufficient privileges to perform this. ${grailsApplication.config.gbifRegistrationRole} role required"
            redirect(action: "show", id: params.uid ?: params.id)
        }
    }

    def updateConsumers(){
        def pg = get(params.id)
        def newConsumers = params.consumers.tokenize(',')
        def oldConsumers = (pg.consumerCollections + pg.consumerInstitutions).collect { it.uid }
        // create new links
        newConsumers.each { String nc ->
            if (!(nc in oldConsumers)) {
                DataResource.withTransaction {
                    if (nc[0..1] == 'co') {
                        Collection c = Collection.findByUid(nc)
                        pg.consumerCollections.add(c)
                        pg.save(flush: true)
                    } else {
                        Institution i = Institution.findByUid(nc)
                        pg.consumerInstitutions.add(i)
                        pg.save(flush: true)
                    }
                    auditLog(pg, 'INSERT', 'consumer', '', nc, pg)
                    log.info "created link from ${pg.uid} to ${nc}"
                }
            }
        }
        // remove old links - NOTE only for the variety (collection or institution) that has been returned
        oldConsumers.each { String oc ->
            if (!(oc in newConsumers) && oc[0..1] == params.source) {
                DataProvider.withTransaction {
                    log.info "deleting link from ${pg.uid} to ${oc}"
                    if (oc[0..1] == 'co') {
                        Collection c = Collection.findByUid(oc)
                        pg.consumerCollections.remove(c)
                        pg.save(flush: true)
                    } else {
                        Institution i = Institution.findByUid(oc)
                        pg.consumerInstitutions.remove(i)
                        pg.save(flush: true)
                    }
                    auditLog(pg, 'DELETE', 'consumer', oc, '', pg)

                }
            }
        }
        flash.message =
          "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
        redirect(action: "show", id: pg.uid)
    }

    def importDirOfDwcA(){
        def dir = new File(params.dir)
        if(dir.exists()){
            def filesImported = dataImportService.importDirOfDwCA(params.dir)
            render "${filesImported.size()} archives imported"
        } else {
            render "Unable to find directory"
        }
    }

    def reimportMetadata(){
        def count = dataImportService.reimportMetadataFromArchives()
        render "Updated ${count} resources"
    }

    /**
     * Get the instance for this entity based on either uid or DB id.
     *
     * @param id UID or DB id
     * @return the entity of null if not found
     */
    protected ProviderGroup get(id) {
        if (id.size() > 2) {
            if (id[0..1] == DataResource.ENTITY_PREFIX) {
                return providerGroupService._get(id)
            }
        }
        // else must be long id
        long dbId
        try {
            dbId = Long.parseLong(id)
        } catch (NumberFormatException e) {
            return null
        }
        return DataResource.get(dbId)
    }

    static def entitySpecificDescriptionProcessing(params) {
        if (params?.suitableFor != 'other' && params?.suitableForOtherDetail) {
            params.suitableForOtherDetail = ''
        }
    }
}
