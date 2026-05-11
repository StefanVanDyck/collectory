package au.org.ala.collectory

import au.org.ala.PermissionRequired
import au.org.ala.collectory.resources.PP
import grails.converters.JSON
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.multipart.MultipartFile
import java.text.NumberFormat
import java.text.ParseException
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * This is a base class for all provider group entities types.
 *
 * It provides common code for shared attributes like contacts.
 */
//@PermissionRequired(roles=['ROLE_EDITOR', 'ROLE_ADMIN'])
abstract class ProviderGroupController {

    String entityName = "ProviderGroup"
    String entityNameLower = "providerGroup"
    int TRUNCATE_LENGTH = 255

    def idGeneratorService, collectoryAuthService, metadataService, gbifService, dataImportService, providerGroupService, activityLogService
    def externalIdentifierService

    /*
     * Access control
     *
     * All methods require EDITOR role.
     * Edit methods require ADMIN or the user to be an administrator for the entity.
     */

    // helpers for subclasses
    protected username = {
        collectoryAuthService?.username() ?: 'unavailable'
    }

    protected isAdmin () {
        //It requires AlaServices annotation to inject info to user profile
        collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN) ?: false
    }
    /*
     End access control
     */

    /**
     * List providers for institutions/collections
     */
    def showProviders = {
        def provs = []
        if (params.id[0..1] == 'co') {
            Collection c = Collection.findByUid(params.uid)
            provs = c.providerDataProviders.collect { it.uid } + c.providerDataResources.collect { it.uid }
        } else {
            Institution c = Institution.findByUid(params.uid)
            provs = c.providerDataProviders.collect { it.uid } + c.providerDataResources.collect { it.uid }
        }
        render provs as JSON
    }

    /**
     * List consumers of data resources/providers
     */
    def showConsumers = {
        def cons = []
        if (params.id[0..1] == 'dr') {
            DataResource c = DataResource.findByUid(params.uid)
            cons = c.consumerCollections.collect { it.uid } + c.consumerInstitutions.collect { it.uid }
        } else {
            DataProvider c = DataProvider.findByUid(params.uid)
            cons = c.consumerCollections.collect { it.uid } + c.consumerInstitutions.collect { it.uid }
        }
        render cons as JSON
    }

    /**
     * Checks for optimistic lock failure
     *
     * @param pg the entity being updated
     * @param view the view to return to if lock fails
     */
    def checkLocking = { pg, view ->
        if (params.version) {
            def version = params.version.toLong()
            if (pg.version > version) {
                pg.errors.rejectValue("version", "default.optimistic.locking.failure",
                        [message(code: "${pg.urlForm()}.label", default: pg.entityType())] as Object[],
                        message(code: "provider.group.controller.02", default: "Another user has updated this") + " ${pg.entityType()} " + message(code: "provider.group.controller.03", default: "while you were editing. This page has been refreshed with the current values."))
                response.setHeader("Content-type", "text/plain; charset=UTF-8")
                render(view: view, model: [command: pg])
            }
            return pg.version > version
        }
    }

    /**
     * Edit base attributes.
     * @param id
     */
    def edit = {
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        } else {
            // are they allowed to edit
            if (isAuthorisedToEdit(pg.uid)) {
                params.page = params.page ?: '/shared/base'
                def suitableFor = providerGroupService.getSuitableFor()
                render(view:params.page, model:[command: pg, target: params.target, suitableFor: suitableFor])
            } else {
                response.setHeader("Content-type", "text/plain; charset=UTF-8")
                render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
            }
        }
    }

    def editAttributions = {
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        } else {
            // are they allowed to edit
            if (isAuthorisedToEdit(pg.uid)) {
                render(view: '/shared/attributions', model:[BCI: pg.hasAttribution('at1'), CHAH: pg.hasAttribution('at2'),
                        CHACM: pg.hasAttribution('at3'), command: pg])
            } else {
                response.setHeader("Content-type", "text/plain; charset=UTF-8")
                render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
            }
        }
    }

    /**
     * Create a new entity instance.
     *
     */
    def create = {
        def name = params.name ?: message(code: "provider.group.controller.05", default: "enter name")
        //def name = message(code: 'provider.group.controller.05', default: 'enter name')
        //def name = 'enter name'
        ProviderGroup pg
        switch (entityName) {
            case Collection.ENTITY_TYPE:
                pg = new Collection(
                        uid: idGeneratorService.getNextCollectionId(),
                        name: name,
                        userLastModified: collectoryAuthService?.username(),
                        southCoordinate: 0, northCoordinate: 0, westCoordinate: 0, eastCoordinate: 0
                )
                if (params.institutionUid && Institution.findByUid(params.institutionUid)) {
                    pg.institution = Institution.findByUid(params.institutionUid)
                }
                break
            case Institution.ENTITY_TYPE:
                pg = new Institution(uid: idGeneratorService.getNextInstitutionId(), name: name, userLastModified: collectoryAuthService?.username(), gbifCountryToAttribute: grailsApplication.config.gbifDefaultEntityCountry); break
            case DataProvider.ENTITY_TYPE:
                pg = new DataProvider(uid: idGeneratorService.getNextDataProviderId(), name: name, userLastModified: collectoryAuthService?.username(), gbifCountryToAttribute: grailsApplication.config.gbifDefaultEntityCountry); break
            case DataResource.ENTITY_TYPE:
                pg = new DataResource(uid: idGeneratorService.getNextDataResourceId(), name: name, userLastModified: collectoryAuthService?.username())
                if (params.dataProviderUid && DataProvider.findByUid(params.dataProviderUid)) {
                        pg.dataProvider = DataProvider.findByUid(params.dataProviderUid)
                }
                break
            case DataHub.ENTITY_TYPE:
                pg = new DataHub(uid: idGeneratorService.getNextDataHubId(), name: name, userLastModified: collectoryAuthService?.username()); break
        }

        DataResource.withTransaction {
            if (!pg.hasErrors() && pg.save(flush: true)) {
                // add the user as a contact if specified
                if (params.addUserAsContact) {
                    addUserAsContact(pg, params)
                }
                flash.message = "${message(code: 'default.created.message', args: [message(code: "${pg.urlForm()}", default: pg.urlForm()), pg.uid])}"
                redirect(action: "show", id: pg.uid)
            } else {
                flash.message = message(code: "provider.group.controller.06", default: "Failed to create new") + " ${entityName}"
                redirect(controller: 'admin', action: 'index')
            }
        }
    }

    /**
     * Adds the current user as a contact for the specified entity.
     *
     * Used when creating new entities.
     * @param pg the entity
     * @param params values for contact fields if the contact does not already exist
     */
    void addUserAsContact(ProviderGroup pg, params) {
        def user = collectoryAuthService?.username()
        // find contact
        Contact c = Contact.findByEmail(user)
        if (!c) {
            // create from params
            c = new Contact(email: user,
                userLastModified: user,
                firstName: params.firstName ?: null,
                lastName: params.lastName ?: null,
                title: params.title ?: null,
                phone: params.phone ?: null,
                publish: (params.publish == 'true')
            )
            c.save(flush:true)
            if (c.hasErrors()) {
                c.errors.each {
                    log.debug("Error saving new contact for ${user} - ${it}")
                }
            }
        }
        pg.addToContacts(c, params.role ?: 'editor', true, false, user)
    }

    def cancel = {
        if (params.returnTo) {
            redirect(uri: params.returnTo)
        } else {
            redirect(action: "show", id: params.uid ?: params.id)
        }
    }

    /**
     * This does generic updating from a form. Works for all properties that can be bound by default.
     */
    def genericUpdate = { pg, view ->
        if (pg) {
            if (checkLocking(pg,view)) { return }

            pg.properties = params
            pg.userLastModified = collectoryAuthService?.username()

            if (!pg.hasErrors()) {
                DataResource.withTransaction {
                    pg.save(flush: true)
                }
                flash.message =
                  "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.uid)
            }
            else {
                render(view: view, model: [command: pg])
            }
        } else {
            flash.message =
                "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    /**
     * Update base attributes
     */
    def updateBase = { //BaseCommand cmd ->

        BaseCommand cmd = new BaseCommand()
        bindData(cmd, params)

        def result = providerGroupService.updateBase(params)
        def pg = result.pg

        if (pg && result.success) {
            flash.message =
                "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
            redirect(action: "show", id: pg.uid)
        } else {
            render(view: "/shared/base", model: [command: pg])
        }
    }

    /**
     * Update descriptive attributes
     */
    def updateDescription = {
        def result = providerGroupService.updateDescription(params)
        def pg = result.pg
        if (pg) {
            if (result.success){
                flash.message =
                        "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.uid)
            } else {
                render(view: "description", model: [command: pg])
            }
        } else {
            flash.message =
                    "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def entitySpecificDescriptionProcessing(pg, params) {
        // default is to do nothing
        // sub-classes override to do specific processing
    }

    /**
     * Update location attributes
     */
    def updateLocation = {

        def pg = get(params.id)
        if (pg) {
            if (checkLocking(pg,'/shared/location')) { return }

            // special handling for lat & long
            def latitude = parseCoordinate(params.latitude)
            def longitude = parseCoordinate(params.longitude)

            // special handling for embedded address - need to create address obj if none exists and we have data
            if (!pg.address && [params.address?.street, params.address?.postBox, params.address?.city,
                params.address?.state, params.address?.postcode, params.address?.country].join('').size() > 0) {
                pg.address = new Address()
            }

            pg.properties = params
            pg.latitude = latitude
            pg.longitude = longitude
            pg.userLastModified = collectoryAuthService?.username()

            if (!pg.hasErrors() ) {
                DataResource.withTransaction {
                    pg.save(flush: true)
                }
                flash.message =
                        "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.uid)
            } else {
                render(view: "/shared/location", model: [command: pg])
            }
        } else {
            flash.message =
                "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def updateTaxonomyHints = {
        def pg = get(params.id)
        if (pg) {
            if (checkLocking(pg,'/shared/editTaxonomyHints')) { return }

            // handle taxonomy hints
            def ranks = params.findAll { key, value ->
                key.startsWith('rank_') && value
            }
            def hints = ranks.sort().collect { key, value ->
                def idx = key.substring(5)
                def name = params."name_${idx}"
                return ["${value}": name]
            }
            def th = pg.taxonomyHints ? JSON.parse(pg.taxonomyHints) : [:]
            th.coverage = hints
            pg.taxonomyHints = th as JSON

            pg.userLastModified = collectoryAuthService?.username()
            if (!pg.hasErrors() ) {
                DataResource.withTransaction {
                    pg.save(flush: true)
                }
                flash.message =
                        "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.uid)
            }
            else {
                render(view: "/shared/editTaxonomyHints", model: [command: pg])
            }
        } else {
            flash.message =
                "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def updateTaxonomicRange = {
        def pg = get(params.id)
        if (pg) {
            if (checkLocking(pg,'/shared/taxonomicRange')) { return }

            // handle taxonomic range
            def rangeList = params.range.tokenize(',')
            def th = pg.taxonomyHints ? JSON.parse(pg.taxonomyHints) : [:]
            th.range = rangeList
            pg.taxonomyHints = th as JSON

            pg.userLastModified = collectoryAuthService?.username()
            if (!pg.hasErrors()) {
                DataResource.withTransaction {
                    pg.save(flush: true)
                }

                flash.message =
                  "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.uid)
            }
            else {
                render(view: "/shared/taxonomicRange", model: [command: pg])
            }
        } else {
            flash.message =
                "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }

    def updateExternalIdentifiers = {
        def pg = get(params.id)
        if (pg) {
            if (checkLocking(pg,'/shared/editExternalIdentifiers')) { return }

            // if there isn't a source, discard it
            def sources = params.findAll { key, value ->
                key.startsWith('source_') && value
            }

            // remove existing external identifiers
            if (pg.externalIdentifiers) {
                def existing = pg.externalIdentifiers
                pg.externalIdentifiers = []

                DataResource.withTransaction {
                    pg.save(flush: true)
                }

                existing.each { ext -> DataResource.withTransaction { ext.delete(flush: true) } }
            }

            // add new external identifiers
            sources.sort().collect { key, value ->
                def idx = key.substring(7)
                def source = params[key]
                def identifier = params."identifier_${idx}"
                def uri = params."uri_${idx}"
                if (!uri)
                    uri = null
                externalIdentifierService.addExternalIdentifier(pg.uid, identifier, source, uri)
            }

            pg.userLastModified = collectoryAuthService?.username()
            if (!pg.hasErrors()) {
                DataResource.withTransaction {
                    pg.save(flush: true)
                }
                flash.message =
                        "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                redirect(action: "show", id: pg.uid)
            }
            else {
                render(view: "/shared/editExternalIdentifiers", model: [command: pg])
            }
        } else {
            flash.message =
                    "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }

    }

    def updateContactRole = {
        def contactFor = ContactFor.get(params.contactForId)
        if (contactFor) {
            contactFor.properties = params
            contactFor.userLastModified = collectoryAuthService?.username()
            if (!contactFor.hasErrors()) {
                ContactFor.withTransaction {
                    contactFor.save(flush: true)
                }
                flash.message = "${message(code: 'contactRole.updated.message')}"
                redirect(action: "edit", id: params.id, params: [page: '/shared/showContacts'])
            } else {
                render(view: '/shared/contactRole', model: [command: providerGroupService._get(params.id), cf: contactFor])
            }

        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'contactFor.label', default: "Contact for ${entityNameLower}", args: [entityNameLower]), params.contactForId])}"
            redirect(action: "show", id: params.id)
        }
    }

    def addContact = {
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        } else {
            if (isAuthorisedToEdit(pg.uid)) {
                Contact contact = Contact.get(params.addContact)
                if (contact) {
                    pg.addToContacts(contact, "editor", true, false, collectoryAuthService?.username())
                    redirect(action: "edit", params: [page:"/shared/showContacts"], id: params.id)
                }
            } else {
                response.setHeader("Content-type", "text/plain; charset=UTF-8")
                render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
            }
        }
    }

    def addNewContact = {
        def pg = get(params.id)
        def contact = Contact.get(params.contactId)
        if (contact && pg) {
            // add the contact to the collection
            pg.addToContacts(contact, "editor", true, false, collectoryAuthService?.username())
            redirect(action: "edit", params: [page:"/shared/showContacts"], id: pg.uid)
        } else {
            if (!pg) {
                flash.message = message(code: "provider.group.controller.07", default: "Contact was created but") + " ${entityNameLower} " + message(code: "provider.group.controller.08", default: "could not be found. Please edit") + " ${entityNameLower} " + message(code: "provider.group.controller.09", default: "and add contact from existing.")
                redirect(action: "list")
            } else {
                if (isAuthorisedToEdit(pg.uid)) {
                    // contact must be null
                    flash.message = message(code: "provider.group.controller.10", default: "Contact was created but could not be added to the") + " ${pg.urlForm()}. " + message(code: "provider.group.controller.11", default: "Please add contact from existing.")
                    redirect(action: "edit", params: [page:"/shared/showContacts"], id: pg.uid)
                } else {
                    response.setHeader("Content-type", "text/plain; charset=UTF-8")
                    render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
                }
            }
        }
    }

    def removeContact = {
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        } else {
            // are they allowed to edit
            if (isAuthorisedToEdit(pg.uid)) {
                ContactFor cf = ContactFor.get(params.idToRemove)
                if (cf) {
                    ContactFor.withTransaction {
                        cf.delete(flush: true)
                    }
                    redirect(action: "edit", params: [page:"/shared/showContacts"], id: params.id)
                }
            } else {
                response.setHeader("Content-type", "text/plain; charset=UTF-8")
                render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
            }
        }
    }


    def editRole = {
        def contactFor = ContactFor.get(params.id)
        if (!contactFor) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'contactFor.label', default: "Contact for ${entityNameLower}", args: [entityNameLower]), params.id])}"
            redirect(action: "list")
        } else {
            ProviderGroup pg = providerGroupService._get(contactFor.entityUid)
            if (pg) {
                // are they allowed to edit
                if (isAuthorisedToEdit(pg.uid)) {
                    render(view: '/shared/contactRole', model: [command: pg, cf: contactFor, returnTo: params.returnTo])
                } else {
                    response.setHeader("Content-type", "text/plain; charset=UTF-8")
                    render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
                }
            } else {
                flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'contactFor.entityUid.label', default: "Entity for ${entityNameLower}", args: [entityNameLower]), params.id])}"
                redirect(action: "list")
            }
        }
    }
    /**
     * Displays the form for loading a single pre-downloaded resource from GBIF. It assumes that there is a single resource
     * in the file.
     */
    def gbifUpload = {
    }

    /**
     * Uploads the supplied GBIF file creating a new data resource based on the supplied EML details
     */

    def downloadGBIFFile = {

        log.info("Downloading file: " + params.url)

        try {
            def dr = gbifService.createGBIFResourceFromArchiveURL(params.url)
            if (dr){
                render(text:([success:true, dataResourceName:dr.name, dataResourceUid: dr.uid] as JSON).toString(),
                        encoding:"UTF-8", contentType: "application/json")
            } else {
                render(text:([success:false] as JSON).toString(), encoding:"UTF-8", contentType: "application/json")
            }

        } catch (Exception e){
            log.error(e.getMessage(), e)
            render(text:([success:false] as JSON).toString(), encoding:"UTF-8", contentType: "application/json")
        }
    }

    def upload = {
        def pg = get(params.id)
        if (!pg) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "upload")
        } else {
            // are they allowed to edit
            if (isAuthorisedToEdit(pg.uid)) {
                def connectionParams = metadataService.getConnectionParameters()
                // set the default value for 'darwin core terms that uniquely identify a record'
                try {
                    JSON.parse(pg.connectionParameters).each { k, v ->

                        var item = connectionParams.find { ck, cv -> cv.paramName == k }

                        if (item?.value) {
                            var defaultValue = v
                            if (item.value.paramName == 'termsForUniqueKey') {
                                defaultValue = v.join(',').replaceAll('"',"")
                            } else if (item.value.type == 'delimiter') {
                                def str = v
                                str = str.replaceAll('HT', PP.HT_CHAR)
                                str = str.replaceAll('LF', PP.LF_CHAR)
                                str = str.replaceAll('VT', PP.VT_CHAR)
                                str = str.replaceAll('FF', PP.FF_CHAR)
                                str = str.replaceAll('CR', PP.CR_CHAR)
                                defaultValue = str
                            } else if (item.value.paramName == 'url') {
                                if (v instanceof List) {
                                    def normalised = []
                                    v.each {
                                        if (it.trim().length() > 0) {
                                            normalised << it.trim()
                                        }
                                    }
                                    defaultValue = normalised.join(',').replaceAll('"',"")
                                }
                            }

                            item.value.putAt('defaultValue', defaultValue)
                        }
                    }
                } catch (ignored) {
                }
                render(view:'upload', model:[
                        instance: pg,
                        connectionProfiles: metadataService.getConnectionProfilesWithFileUpload(),
                        connectionParams: connectionParams
                ])
            } else {
                response.setHeader("Content-type", "text/plain; charset=UTF-8")
                render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
            }
        }
    }


    def uploadDataFile = {

        //get the UID
        def dataResource = get(params.id)

        def f = request.getFile('myFile')
        if (f.empty) {
            flash.message = message(code: "provider.group.controller.12", default: "file cannot be empty")
            response.setHeader("Content-type", "text/plain; charset=UTF-8")
            render(view: 'upload')
            return
        }

        dataImportService.importDataFileForDataResource(dataResource, f, params, true)
        redirect([controller: 'dataResource', action: 'show', id: dataResource.uid])
    }

    /**
     * Get the instance for this entity based on either uid or DB id.
     * All sub-classes must implement this method.
     *
     * @param id UID or DB id
     * @return the entity of null if not found
     */
    abstract protected ProviderGroup get(id)

    /**
     * Update images
     */
    def updateImages = {
        def pg = get(params.id)
        def target = params.target ?: "imageRef"
        if (pg) {
            if (checkLocking(pg,'/shared/images')) { return }

            // special handling for uploading image
            // we need to account for:
            //  a) upload of new image
            //  b) change of metadata for existing image
            // removing an image is handled separately
            MultipartFile file
            switch (target) {
                case 'imageRef': file = params.imageFile; break
                case 'logoRef': file = params.logoFile; break
            }
            if (file?.size) {  // will only have size if a file was selected
                // save the chosen file
                if (file.size < 200000) {   // limit file to 200Kb
                    //sanitize filename
                    def filename = file.getOriginalFilename().replaceAll('[^\\u0020-\\u00FF]', '_')
                    log.debug "filename=${filename}"

                    // update filename
                    pg."${target}" = new Image(file: filename)
                    String subDir = pg.urlForm()

                    def colDir = new File(grailsApplication.config.repository.location.images as String, subDir)
                    colDir.mkdirs()
                    File f = new File(colDir, filename)
                    log.debug "saving ${filename} to ${f.absoluteFile}"
                    file.transferTo(f)
                    activityLogService.log(collectoryAuthService?.username(), collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN), Action.UPLOAD_IMAGE, filename)
                } else {
                    pg.errors.rejectValue('imageRef', 'image.too.big', message(code: "provider.group.controller.13", default: "The image you selected is too large. Images are limited to 200KB."))
                    render(view: "/shared/images", model: [command: pg, target: target])
                    return
                }
            }
            pg.properties = params
            pg.userLastModified = collectoryAuthService?.username()

            Contact.withTransaction {
                if (!pg.hasErrors() && pg.save(flush: true)) {
                    flash.message = "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                    redirect(action: "show", id: pg.uid)
                } else {
                    render(view: "/shared/images", model: [command: pg, target: target])
                }
            }
        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityName), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }


    def removeImage = {
        def pg = get(params.id)
        if (pg) {
            if (isAuthorisedToEdit(pg.uid)) {
                if (checkLocking(pg,'/shared/images')) { return }

                if (params.target == 'logoRef') {
                    pg.logoRef = null
                } else {
                    pg.imageRef = null
                }
                pg.userLastModified = collectoryAuthService?.username()

                Contact.withTransaction {
                    if (!pg.hasErrors() && pg.save(flush: true)) {
                        flash.message = "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                        redirect(action: "show", id: pg.uid)
                    } else {
                        render(view: "/shared/images", model: [command: pg])
                    }
                }
            } else {
                response.setHeader("Content-type", "text/plain; charset=UTF-8")
                render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
            }
        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }


    def updateAttributions = {
        def pg = get(params.id)
        if (pg) {
            if (checkLocking(pg,'/shared/attributions')) { return }

            if (params.BCI && !pg.hasAttribution('at1')) {
                pg.addAttribution 'at1'
            }
            if (!params.BCI && pg.hasAttribution('at1')) {
                pg.removeAttribution 'at1'
            }
            if (params.CHAH && !pg.hasAttribution('at2')) {
                pg.addAttribution 'at2'
            }
            if (!params.CHAH && pg.hasAttribution('at2')) {
                pg.removeAttribution 'at2'
            }
            if (params.CHACM && !pg.hasAttribution('at3')) {
                pg.addAttribution 'at3'
            }
            if (!params.CHACM && pg.hasAttribution('at3')) {
                pg.removeAttribution 'at3'
            }

            if (pg.isDirty()) {
                pg.userLastModified = collectoryAuthService?.username()
                Contact.withTransaction {
                    if (!pg.hasErrors() && pg.save(flush: true)) {
                        flash.message =
                                "${message(code: 'default.updated.message', args: [message(code: "${pg.urlForm()}.label", default: pg.entityType()), pg.uid])}"
                        redirect(action: "show", id: pg.uid)
                    } else {
                        render(view: "description", model: [command: pg])
                    }
                }
            } else {
                redirect(action: "show", id: pg.uid)
            }
        } else {
            flash.message =
                "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "show", id: params.id)
        }
    }


    def delete = {
        def pg = get(params.id)
        if (pg) {
            if (collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN) || !grailsApplication.config.security.oidc.enabled.toBoolean()) {
                def name = pg.name
                log.info ">>${collectoryAuthService?.username()} deleting ${entityName} " + name
                activityLogService.log collectoryAuthService?.username(), collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN), pg.uid, Action.DELETE
                try {
                    Contact.withTransaction {
                        // remove contact links (does not remove the contact)
                        ContactFor.findAllByEntityUid(pg.uid).each {
                            log.info "Removing link to contact " + it.contact?.buildName()
                            it.delete(flush: true)
                        }
                        // delete
                        pg.delete(flush: true)
                    }
                    flash.message = "${message(code: 'default.deleted.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), name])}"
                    redirect(action: "list")
                } catch (DataIntegrityViolationException e) {
                    flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), name])}"
                    redirect(action: "show", id: params.id)
                }
            } else {
                response.setHeader("Content-type", "text/plain; charset=UTF-8")
                render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
            }
        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        }
    }


    def showChanges = {
        def instance = get(params.id)
        if (instance) {
            // get audit records
            def changes = AuditLogEvent.findAllByUri(instance.uid,[sort:'lastUpdated',order:'desc'])
            render(view:'/shared/showChanges', model:[changes:changes, instance:instance])
        } else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: "${entityNameLower}.label", default: entityNameLower), params.id])}"
            redirect(action: "list")
        }
    }

    def getChanges(uid) {
        // get audit records
        return AuditLogEvent.findAllByUri(uid,[sort:'lastUpdated',order:'desc',max:10])
    }

    protected String toJson(param) {
        if (!param) {
            return ""
        }
        if (param instanceof String) {
            // single value
            return ([param] as JSON).toString()
        }
        def list = param.collect {
            it.toString()
        }
        return (list as JSON).toString()
    }

    protected String toSpaceSeparatedList(param) {
        if (!param) {
            return ""
        }
        if (param instanceof String) {
            // single value
            return param
        }
        return param.join(' ')
    }

    protected void auditLog(ProviderGroup pg, String eventName, String property, String oldValue, String newValue, Object persistedObject) {
        def audit = new AuditLogEvent(
                  actor: username(),
                  uri: pg.uid,   /* MEW repurposing of uri */
                  className: pg.getClass().name,
                  eventName: eventName,
                  persistedObjectId: persistedObject.id?.toString(),
                  persistedObjectVersion: persistedObject.version,
                  propertyName: property,
                  oldValue: truncate(oldValue),
                  newValue: truncate(newValue)
          )
        audit.dateCreated = new Date()
        audit.save()
    }

    private String truncate(str) {
        return (str?.length() > TRUNCATE_LENGTH) ? str?.substring(0, TRUNCATE_LENGTH) : str
    }

    protected boolean isAuthorisedToEdit(uid) {
        if (isAdmin()) {
            return true
        } else {
            def email = collectoryAuthService.authService.email
            ProviderGroup pg = providerGroupService?._get(uid)
            if (email && pg) {
                if(pg){
                    return pg.isAuthorised(email)
                }
            }
        }
        return false
    }

    private Double parseCoordinate(Object value) {
        if (value == null || value.toString().trim().isEmpty()) return -1
        try {
            return Double.parseDouble(value.toString().replace(",", "."))
        } catch (NumberFormatException e) {
            return -1
        }
    }
}

