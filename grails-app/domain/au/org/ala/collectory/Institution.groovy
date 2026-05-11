package au.org.ala.collectory

class Institution implements ProviderGroup, Serializable {

    static final String ENTITY_TYPE = 'Institution'
    static final String ENTITY_PREFIX = 'in'

    static auditable = [ignore: ['version','dateCreated','lastUpdated','userLastModified']]

    String institutionType      // the type of institution, eg herbarium, library

    String childInstitutions    // space-separated list of UIDs of institutions that this institution administers

    // GBIF uses 2 digit codes, however ALA has historically stored 3 digits ISO code to this field.
    String gbifCountryToAttribute

    // an institution may have many collections
    static hasMany = [collections: Collection, externalIdentifiers: ExternalIdentifier]

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

        // based on TDWG Ontology - http://code.google.com/p/tdwg-ontology/source/browse/trunk/ontology/voc/InstitutionType.rdf
        institutionType(nullable:true, maxSize:45)
        collections(nullable:true)
        childInstitutions(nullable:true)
        gbifCountryToAttribute(nullable:false, maxSize: 3)
    }

    static transients = ['summary','mappable']

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
    }

    /**
     * Returns true if:
     *  a) has membership of a collection network (hub) (assumed that all hubs are partners)
     *  b) has isALAPartner set
     *
     * NOTE: restriction on abstract methods
     */
    boolean isALAPartner() {
        if (networkMembership != null && networkMembership != "[]") {
            return true
        } else {
            return this.isALAPartner
        }
    }

    /**
     * Returns true if the group can be mapped.
     *
     * @return
     */
    boolean canBeMapped() {
        return latitude != 0.0 && latitude != -1 && longitude != 0.0 && longitude != -1
    }

    Map inheritedLatLng() {
        return null
    }

    long dbId() { return id }

    String entityType() {
        return ENTITY_TYPE
    }

    /**
     * List of collections held directly by this institution or by an institution that this one administers.
     *
     * @return List of Collection
     */
    def listCollections() {
        List result = collections.collect { it }
        if (childInstitutions) {
            def list = childInstitutions.tokenize(' ')
            Institution.createCriteria().list(fetch: [collections: 'join']) {
                inList ('uid', list )
            }.each {
                result.addAll it.listCollections()
            }
        }
        return result
    }

    /**
     * List of institutions that include this as a child institution.
     *
     * Note this is not very efficient as the relationship is modelled solely in the parent.
     * Also the hits need to be filtered as searching for in7 will also hit in75.
     * TODO: this should be refactored as json if not a true one-to-many link.
     * @return list of Institution
     */
    def listParents() {
        def list = []
        Institution.findAll("from Institution as i where i.childInstitutions like ?0", [this.uid]).each { inst->
            def parents = inst.childInstitutions?.tokenize(' ')
            parents.each { child ->
                if (child == uid) {
                    list << inst
                }
            }
        }
        return list
    }


    /**
     * List of institutions that 'belong' to this institution.
     * @return List of Institution
     */
    def listChildren() {
        def list = []
        childInstitutions?.tokenize(' ').each {
            list << Institution.findByUid(it)
        }
        return list
    }

    /**
     * This returns a list of child collections.
     *
     * @return list of Collection
     */
    @Override
    def children() {
        return collections
    }

    /**
     * List the uids that identify this institution and all its descendant institutions.
     *
     * @return list of UID
     */
    List<String> descendantUids() {
        def uids = [uid]
        if (childInstitutions) {
            childInstitutions.tokenize(' ').each {
                def child = au.org.ala.collectory.Institution.findByUid(it as String)
                if (child) {
                    uids += child.descendantUids()
                }
            }
        }
        return uids
    }

//    List<Attribution> getInstitutionAttributionList() {
//        List<Attribution> list = getAttributionList();
//        // add institution
//        list << new Attribution(name: name, url: websiteUrl, uid: uid)
//        return list
//    }


    List<String> getLinkedDataResources() {
        return providerDataProviders.collect { it.uid } + providerDataResources.collect { it.uid }
    }

    def getProviderDataResources() {
        def c = DataResource.createCriteria()
        def result = c.list {
            consumerInstitutions {
                idEq(this.id)
            }
        }
        return result
    }

    def getProviderDataProviders() {
        def c = DataProvider.createCriteria()
        def result = c.list {
            consumerInstitutions {
                idEq(this.id)
            }
        }
        return result
    }
}
