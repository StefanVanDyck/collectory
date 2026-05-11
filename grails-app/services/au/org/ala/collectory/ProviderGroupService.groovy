package au.org.ala.collectory

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import grails.util.Holders


@Transactional
class ProviderGroupService {

    def collectoryAuthService
    def grailsApplication
    def messageSource
    def authService
    def siteLocale = new Locale.Builder().setLanguageTag(Holders.config.siteDefaultLanguage as String).build()

    def serviceMethod() {}

    static transients = ['primaryInstitution', 'primaryContact', 'memberOf', 'networkTypes', 'mappable','ALAPartner',
                         'primaryPublicContact','publicContactsPrimaryFirst','contactsPrimaryFirst', 'authorised']

    /**
     * Returns the form that can be used in url path, ie as a controller name, one of:
     * collection, institution, dataProvider, dataResource, dataHub
     *
     * @param entityType short class name of entity
     * @return
     */
    String urlFormOfEntityType(String entityType) {
        entityType[0..0].toLowerCase() + entityType[1..-1]
    }

    /**
     * Returns the form that can be used in url path, eg as a controller name, one of:
     * collection, institution, dataProvider, dataResource, dataHub - based on the uid.
     *
     * @param uid
     */
    String urlFormFromUid(String uid) {
        urlFormOfEntityType(entityTypeFromUid(uid))
    }

    /**
     * Returns the entity type, one of:
     * Collection, Institution, DataProvider, DataResource, DataHub
     *
     * @param uid
     * @return
     */
    String entityTypeFromUid(String uid) {
        if (!uid) { return "" }
        switch (uid[0..1]) {
            case Institution.ENTITY_PREFIX: return Institution.ENTITY_TYPE
            case Collection.ENTITY_PREFIX: return Collection.ENTITY_TYPE
            case DataProvider.ENTITY_PREFIX: return DataProvider.ENTITY_TYPE
            case DataResource.ENTITY_PREFIX: return DataResource.ENTITY_TYPE
            case DataHub.ENTITY_PREFIX: return DataHub.ENTITY_TYPE
        }
    }

    /**
     * Returns the form that can be used in plain text, one of:
     * collection, institution, data provider, data resource, data hub
     *
     * @param uid
     * @return
     */
    String textFormOfEntityType(String uid) {
        String entityType = entityTypeFromUid(uid)
        String result = ""
        entityType.each {
            if (Character.isUpperCase(it[0] as Character)) {
                result += " " + it.toLowerCase()
            } else {
                result += it
            }
        }
        return result
    }

    /**
     * Returns the instance identified by the uid.
     *
     * @param uid
     * @return
     */
    ProviderGroup  _get(String uid) {
        if (!uid || uid.size() < 3) {return null}
        switch (uid[0..1]) {
            case Institution.ENTITY_PREFIX: return Institution.findByUid(uid)
            case Collection.ENTITY_PREFIX: return Collection.findByUid(uid)
            case DataProvider.ENTITY_PREFIX: return DataProvider.findByUid(uid)
            case DataResource.ENTITY_PREFIX: return DataResource.findByUid(uid)
            case DataHub.ENTITY_PREFIX: return DataHub.findByUid(uid)
            default: return null
        }
    }

    ProviderGroup  _getEager(String uid) {
        if (!uid || uid.size() < 3) {return null}
        switch (uid[0..1]) {
            case Institution.ENTITY_PREFIX:
                return Institution.createCriteria().list([fetch: [collections: "join", providerMap: "join", externalIdentifiers: "join", attributions: "join"]]) {
                    eq('uid', uid)
                }[0]
            case Collection.ENTITY_PREFIX:
                return Collection.createCriteria().list([fetch: [institution: "join", providerMap: "join", externalIdentifiers: "join"]]) {
                    eq('uid', uid)
                }[0]
            case DataProvider.ENTITY_PREFIX:
                return DataProvider.createCriteria().list([fetch: [resources: "join", externalIdentifiers: "join", consumerInstitutions: "join", consumerCollections: "join"]]) {
                    eq('uid', uid)
                }[0]
            case DataResource.ENTITY_PREFIX:
                return DataResource.createCriteria().list([fetch: [dataProvider: "join", institution: "join", externalIdentifiers: "join", consumerInstitutions: "join", consumerCollections: "join"]]) {
                    eq('uid', uid)
                }[0]
            case DataHub.ENTITY_PREFIX: return DataHub.findByUid(uid)
            default: return null
        }
    }

    /**
     * Returns the instance identified by the uid.
     *
     * @param uid
     * @return
     */
    ProviderGroup _get(String id, String entityType) {
        try {
            switch (entityType.toLowerCase()) {
                case Institution.ENTITY_TYPE.toLowerCase(): return Institution.findById(id)
                case Collection.ENTITY_TYPE.toLowerCase(): return Collection.findById(id)
                case DataProvider.ENTITY_TYPE.toLowerCase(): return DataProvider.findById(id)
                case DataResource.ENTITY_TYPE.toLowerCase(): return DataResource.findById(id)
                case DataHub.ENTITY_TYPE.toLowerCase(): return DataHub.findById(id)
                default: return null
            }
        } catch (Exception e){
            return null
        }
    }

    def resolveAddress(ProviderGroup pg){
        def addr = pg.resolveAddress()
        if (!addr && (pg instanceof DataProvider || pg instanceof DataResource)) {
            (pg.consumerInstitutions + pg.consumerCollections).find {
                return it && it.resolveAddress()
            }
        }
        return addr
    }

    /**
     * Update base attributes
     */
    def updateBase(params) { //BaseCommand cmd ->
        def pg = _get(params.uid)
        if (pg) {
            // special handling for membership
            pg.networkMembership = toJson(params.networkMembership)
            params.remove('networkMembership')

            pg.properties = params
            pg.userLastModified = collectoryAuthService?.username()
            if (!pg.hasErrors() && pg.save(flush: true)) {
                [success:true, pg:pg]
            } else {
                [success:false, pg:pg]
            }
        } else {
            [success:false]
        }
    }

    /**
     * Update descriptive attributes
     */
    def updateDescription(params) {
        def pg = get(params.uid)
        if (pg) {
            if (checkLocking(pg,'description')) { return [success: false, pg:pg, locked:true] }
            // do any entity specific processing
            entitySpecificDescriptionProcessing(pg, params)
            pg.properties = params
            pg.userLastModified = collectoryAuthService?.username()
            if (!pg.hasErrors() && pg.save(flush: true)) {
                [success: true, pg:pg]
            }
            else {
                [success: false, pg:pg]
            }
        } else {
            [success: false]
        }
    }

    def entitySpecificDescriptionProcessing(entity, params) {
        // default is to do nothing
        // sub-classes override to do specific processing
        // FIXME
        if (entity instanceof Collection){
            CollectionController.entitySpecificDescriptionProcessing(entity, params)
        } else if (entity instanceof DataResource) {
            DataResourceController.entitySpecificDescriptionProcessing(params)
        }
    }

    /**
     * Update location attributes
     */
    def updateLocation(params){
        def pg = get(params.uid)
        if (pg) {
            if (checkLocking(pg,'/shared/location')) { [success: false, pg:pg, locked:true]  }

            // special handling for lat & long
            if (!params.latitude) { params.latitude = -1 }
            if (!params.longitude) { params.longitude = -1 }

            // special handling for embedded address - need to create address obj if none exists and we have data
            if (!pg.address && [params.address?.street, params.address?.postBox, params.address?.city,
                                params.address?.state, params.address?.postcode, params.address?.country].join('').size() > 0) {
                pg.address = new Address()
            }

            pg.properties = params
            pg.userLastModified = collectoryAuthService?.username()
            if (!pg.hasErrors() && pg.save(flush: true)) {
                [success: true, pg:pg]
            } else {
                [success: false, pg:pg]
            }
        } else {
            [success: false]
        }
    }

    def updateTaxonomyHints(params) {
        def pg = get(params.uid)
        if (pg) {
            if (checkLocking(pg,'/shared/editTaxonomyHints')) { [success: false, pg:pg, locked:true]  }

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
            if (!pg.hasErrors() && pg.save(flush: true)) {
                [success: true, pg:pg]
            }
            else {
                [success: false, pg:pg]
            }
        } else {
            [success: false]
        }
    }

    def updateTaxonomicRange(params){
        def pg = get(params.uid)
        if (pg) {
            if (checkLocking(pg,'/shared/taxonomicRange')) { return [success: false, pg:pg, locked:true] }

            // handle taxonomic range
            def rangeList = params.range.tokenize(',')
            def th = pg.taxonomyHints ? JSON.parse(pg.taxonomyHints) : [:]
            th.range = rangeList
            pg.taxonomyHints = th as JSON

            pg.userLastModified = collectoryAuthService?.username()
            if (!pg.hasErrors() && pg.save(flush: true)) {
                [success: true, pg:pg, locked:false]
            }
            else {
                [success: false, pg:pg, locked:false]
            }
        } else {
            [success: false]
        }
    }

    def updateExternalIdentifiers(params){
        def pg = get(params.uid)
        if (pg) {
            if (checkLocking(pg,'/shared/editExternalIdentifiers')) { return [success: false, pg:pg, locked:true] }

            // if there isn't a source, discard it
            def sources = params.findAll { key, value ->
                key.startsWith('source_') && value
            }
            def external = sources.sort().collect { key, value ->
                def idx = key.substring(7)
                def source = params[key]
                def identifier = params."identifier_${idx}"
                def uri = params."uri_${idx}"
                if (!uri)
                    uri = null
                return new ExternalIdentifier(entityUid: pg.uid, source: source, identifier: identifier, uri: uri)
            }
            def existing = pg.externalIdentifiers
            external.each { ext ->
                def old = existing.find { prev -> prev.same(ext) }
                if (!old) {
                    ext.save(flush: true)
                } else {
                    old.uri = ext.uri
                    old.save(flush: true)
                    existing.remove(old)
                }
            }
            // Delete non-matching, existing external IDs
            existing.each { ext -> ext.delete(flush: true) }
            pg.userLastModified = collectoryAuthService?.username()
            if (!pg.hasErrors() && pg.save(flush: true)) {
                [success: true, pg:pg, locked:false]
            }
            else {
                [success: false, pg:pg, locked:false]
            }
        } else {
            [success: false, pg:pg, locked:false]
        }
    }

    def updateContactRole(params) {
        def contactFor = ContactFor.get(params.contactForId)
        if (contactFor) {
            contactFor.properties = params
            contactFor.userLastModified = collectoryAuthService?.username()
            if (!contactFor.hasErrors() && contactFor.save(flush: true)) {
                [success: true, contactFor:contactFor]
            } else {
                [success: false, contactFor:contactFor]
            }
        } else {
            [success: false, contactFor:contactFor]
        }
    }

    def addContact(params) {
        def pg = get(params.uid)
        if (!pg) {
            [success: false]
        } else {
            if (isAuthorisedToEdit(pg.uid)) {
                Contact contact = Contact.get(params.addContact)
                if (contact) {
                    pg.addToContacts(contact, "editor", true, false, collectoryAuthService?.username())
                    [success: true, pg:pg]
                }
            } else {
                [success: false, pg:pg, authorised:false]
            }
        }
    }

    def addNewContact(params) {
        def pg = get(params.uid)
        def contact = Contact.get(params.contactId)
        if (contact && pg) {
            // add the contact to the collection
            pg.addToContacts(contact, "editor", true, false, collectoryAuthService?.username())
            [success: true, pg:pg, authorised:true]
        } else {
            if (!pg) {
                [success: false]
            } else {
                if (isAuthorisedToEdit(pg.uid)) {
                    // contact must be null
                    [success: true, pg:pg, authorised:true]
                } else {
                    [success: false, pg:pg, authorised:false]
                }
            }
        }
    }

    def removeContact(params) {
        def pg = get(params.uid)
        if (!pg) {
            [success: false]
        } else {
            // are they allowed to edit
            if (isAuthorisedToEdit(pg.uid)) {
                ContactFor cf = ContactFor.get(params.idToRemove)
                if (cf) {
                    cf.delete(flush: true)
                }
                [success: true, pg:pg, authorised:true]
            } else {
                [success: false, pg:pg, authorised:false]
            }
        }
    }

    def editRole(params) {
        def contactFor = ContactFor.get(params.id)
        if (!contactFor) {
            [success: false]
        } else {
            ProviderGroup pg = _get(contactFor.entityUid)
            if (pg) {
                // are they allowed to edit
                if (isAuthorisedToEdit(pg.uid)) {
                    [success: true, pg:pg, authorised:true]
                } else {
                    [success: false, pg:pg, authorised:false]
                }
            } else {
                [success: false]
            }
        }
    }

    /**
     * Checks for optimistic lock failure
     *
     * @param pg the entity being updated
     * @param view the view to return to if lock fails
     */
    boolean checkLocking(params, pg) {
//        if (params.version) {
//            def version = params.version.toLong()
//            if (pg.version > version) {
//                log.error(message(code: "provider.group.controller.02", default: "Another user has updated this") + " ${pg.entityType()} " + message(code: "provider.group.controller.03", default: "while you were editing. This page has been refreshed with the current values."))
//                false
//            }
//        }
        false
    }

    /**
     * Get the instance for this entity based on either uid or DB id.
     *
     * @param id UID or DB id
     * @return the entity of null if not found
     */
    protected ProviderGroup get(id) {
        if (id.size() > 2) {
            if (id[0..1] == Collection.ENTITY_PREFIX ||
                    id[0..1] == Institution.ENTITY_PREFIX ||
                    id[0..1] == DataResource.ENTITY_PREFIX ||
                    id[0..1] == DataProvider.ENTITY_PREFIX ||
                    id[0..1] == DataHub.ENTITY_PREFIX) {
                return _get(id)
            }
        }
        // else must be long id
        long dbId
        try {
            dbId = Long.parseLong(id)
        } catch (NumberFormatException e) {
            return null
        }
        return Collection.get(dbId)
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

     boolean isAuthorisedToEdit(uid) {
        if (!grailsApplication.config.security.oidc.enabled.toBoolean() || isAdmin()) {
            return true
        } else {
            def email = authService.email
            if (email) {
                return _get(uid)?.isAuthorised(email)
            }
        }
        return false
    }

    protected isAdmin = {
        collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN) ?: false
    }

    /**
     * Returns the list of provider groups that this contact is a contact for.
     *
     * @return list of ProviderGroup or empty list
     */
    List<ProviderGroup> getContactsFor(Contact contact) {
        List<ProviderGroup> result = []
        ContactFor.findAllByContact(contact).each {
            result << _get(it.entityUid)
        }
        return result
    }

    Map getSuitableFor() {
        // the settings in config is an array so that after JSON.parse the original order can be kept.
        def settings = grailsApplication.config.getProperty('suitableFor', String, '[]')
        return JSON.parse(settings).collectEntries{
            def key = it.keySet().first()
            def val = messageSource.getMessage("dataresource.suitablefor." + key, null, it.values().first(), siteLocale)
            [key, val]
        }
    }
}
