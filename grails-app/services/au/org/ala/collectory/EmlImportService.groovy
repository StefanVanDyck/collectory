package au.org.ala.collectory

import groovy.util.slurpersupport.GPathResult

class EmlImportService {

    def serviceMethod() {}

    def dataLoaderService, collectoryAuthService

    /**
     * Normalize phone number by removing common formatting characters.
     * This allows matching phones like "+34-932565991" and "+34932565991" as the same.
     */
    private String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return ""
        // Remove spaces, hyphens, parentheses, and dots
        return phone.trim().replaceAll(/[\s\-\(\)\.]/, "").toLowerCase()
    }

    /** Collect individual XML para elements together into a single block of text */
    protected def collectParas(GPathResult paras) {
        paras?.list().inject(null, { acc, para -> acc == null ? (para.text()?.trim() ?: "") : acc + " " + (para.text()?.trim() ?: "") })
    }

    public emlFields = [

        guid:  { eml -> eml.@packageId.toString() },
        pubDescription: { eml -> this.collectParas(eml.dataset.abstract?.para) },
        name: { eml -> eml.dataset.title.toString() },
        email: { eml ->  eml.dataset.contact.size() > 0 ? eml.dataset.contact[0]?.electronicMailAddress?.text(): null },
        rights: { eml ->  this.collectParas(eml.dataset.intellectualRights?.para) },
        citation: { eml ->  eml.additionalMetadata?.metadata?.gbif?.citation?.text() },
        state: { eml ->

            def state = ""

            def administrativeAreas = eml.dataset.contact.size() > 0 ? eml.dataset.contact[0]?.address?.administrativeArea: null
            if (administrativeAreas){

                if (administrativeAreas.size() > 1){
                    state = administrativeAreas.first().text()
                } else {
                    state = administrativeAreas.text()
                }
                if (state) {
                    state = this.dataLoaderService.massageState(state)
                }
            }
            state
        },
        phone: { eml ->  eml.dataset.contact.size() > 0 ? eml.dataset.contact[0]?.phone?.text(): null },

        //geographic coverage
        geographicDescription: { eml -> eml.dataset.coverage?.geographicCoverage?.geographicDescription?:'' },
        northBoundingCoordinate: { eml -> eml.dataset.coverage?.geographicCoverage?.boundingCoordinates?.northBoundingCoordinate?:''},
        southBoundingCoordinate: { eml -> eml.dataset.coverage?.geographicCoverage?.boundingCoordinates?.southBoundingCoordinate?:''},
        eastBoundingCoordinate : { eml -> eml.dataset.coverage?.geographicCoverage?.boundingCoordinates?.eastBoundingCoordinate?:''},
        westBoundingCoordinate: { eml -> eml.dataset.coverage?.geographicCoverage?.boundingCoordinates?.westBoundingCoordinate?:''},

        //temporal
        beginDate: { eml -> eml.dataset.coverage?.temporalCoverage?.rangeOfDates?.beginDate?.calendarDate?:''},
        endDate: { eml -> eml.dataset.coverage?.temporalCoverage?.rangeOfDates?.endDate?.calendarDate?:''},

        //additional fields
        purpose: { eml -> eml.dataset.purpose?.para?:''},
        methodStepDescription: { eml -> eml.dataset.methods?.methodStep?.description?.para?:''},
        qualityControlDescription: { eml -> eml.dataset.methods?.qualityControl?.description?.para?:''},

        gbifDoi: { eml ->
            def gbifDoi = null
            eml.dataset.alternateIdentifier?.each {
                def id = it.text()
                if (id && id.startsWith("doi")) {
                    gbifDoi = id
                }
            }
            gbifDoi
        },
        licenseType: { eml -> getLicence(eml).licenseType },
        licenseVersion: { eml -> getLicence(eml).licenseVersion },
        externalIdentifiers: { eml ->
            def identifiers = []
            eml.dataset.alternateIdentifier?.each {
                //todo: parse out source and entityUid if present
                def id = it.text()
                if (id) {
                    ExternalIdentifier ei = new ExternalIdentifier()
                    ei.identifier = id
                    ei.source = id
                    ei.entityUid =  id
                    ei.uri = id
                    identifiers << ei
                }
            }
            return identifiers
        }
    ]


    def getLicence(eml){

        def licenceInfo = [licenseType:'', licenseVersion:'']
        //try and match the acronym to licence
        def rights = this.collectParas(eml.dataset.intellectualRights?.para)

        def matchedLicence = Licence.findByAcronym(rights)
        if (!matchedLicence) {
            //attempt to match the licence
            def licenceUrl = eml.dataset.intellectualRights?.para?.ulink?.@url.text()
            def licence = Licence.findByUrl(licenceUrl)
            if (licence == null) {
                if (licenceUrl.contains("http://")) {
                    matchedLicence = Licence.findByUrl(licenceUrl.replaceAll("http://", "https://"))
                } else {
                    matchedLicence = Licence.findByUrl(licenceUrl.replaceAll("https://", "http://"))
                }
            } else {
                matchedLicence = licence
            }
        }

        if(matchedLicence){
            licenceInfo.licenseType = matchedLicence.acronym
            licenceInfo.licenseVersion = matchedLicence.licenceVersion
        }

        licenceInfo
    }

    /**
     * Extracts a set of properties from an EML document, populating the
     * supplied dataresource, connection params.
     *
     * @param xml
     * @param dataResource
     * @param connParams
     * @return
     */
    def extractContactsFromEml(eml, dataResource) {
        def contacts = []
        def primaryContacts = []
        def processedContactKeys = []  // Track contacts within THIS extraction to avoid duplicates

        emlFields.each { name, accessor ->
            def val = accessor(eml)
            if (val != null) {
                dataResource.setProperty(name, val)
            }
        }

        def addContact = { provider ->
            def hasSurName = provider?.individualName?.surName?.text()?.trim()?.isEmpty() == false
            def hasOrg = provider?.organizationName?.text()?.trim()?.isEmpty() == false
            def hasPosition = provider?.positionName?.text()?.trim()?.isEmpty() == false

            // Only process if there's at least a surname, organization, or position
            if (hasSurName || hasOrg || hasPosition) {
                // Create a unique key for this contact based on its data
                // This allows deduplication within the same EML while preserving different affiliations
                def firstName = provider.individualName?.givenName?.text()?.trim() ?: ""
                def lastName = provider.individualName?.surName?.text()?.trim() ?: ""
                def email = provider.electronicMailAddress?.text()?.trim() ?: ""
                def phone = provider.phone?.text()?.trim() ?: ""
                def org = provider.organizationName?.text()?.trim() ?: ""
                def position = provider.positionName?.text()?.trim() ?: ""
                def userId = provider.userId?.text()?.trim() ?: ""
                def userIdDirectory = provider.userId?.@directory?.text()?.trim() ?: ""

                // Create unique key based on all contact attributes
                // Normalize phone to handle different formatting: +34-932565991 == +34932565991
                def normalizedPhone = normalizePhone(phone)
                def contactKey = "${firstName}|${lastName}|${email}|${normalizedPhone}|${org}|${position}|${userId}|${userIdDirectory}".toLowerCase()

                // Check if we've already processed this exact contact in this EML
                if (!processedContactKeys.contains(contactKey)) {
                    def contact = addOrUpdateContact(provider, dataResource)
                    if (contact) {
                        contacts << contact
                        processedContactKeys << contactKey  // Mark as processed
                        return contact
                    }
                } else {
                    // This exact contact already processed - find it and return it for primaryContacts
                    def existingContact = contacts.find { c ->
                        def cNormalizedPhone = normalizePhone(c.phone ?: '')
                        def cKey = "${c.firstName ?: ''}|${c.lastName ?: ''}|${c.email ?: ''}|${cNormalizedPhone}|${c.organizationName ?: ''}|${c.positionName ?: ''}|${c.userId ?: ''}".toLowerCase()
                        return cKey == contactKey
                    }
                    return existingContact
                }
            }
            return null
        }

        // EML Schema - Process all contacts without deduplication across roles
        // https://eml.ecoinformatics.org/images/eml-party.png
        // However, deduplicate identical contacts within same resource (GBIF approach)

        if (eml.dataset.creator) {
            eml.dataset.creator.each { addContact(it) }
        }

        if (eml.dataset.contact) {
            eml.dataset.contact.each {
                def contact = addContact(it)
                if (contact && !primaryContacts.contains(contact)) {
                    primaryContacts << contact
                }
            }
        }

        if (eml.dataset.metadataProvider) {
            eml.dataset.metadataProvider.each { addContact(it) }
        }

        if (eml.dataset.associatedParty) {
            eml.dataset.associatedParty.each { addContact(it) }
        }

        [contacts: contacts, primaryContacts: primaryContacts]
    }

    /**
     * Add or update a contact from an EML element for a specific DataResource.
     *
     * GBIF-style approach:
     * - Only matches contacts that are already associated with this specific DataResource
     * - Creates new contacts for other resources (dissociated contacts)
     * - Preserves user-set flags like 'publish'
     * - Allows same person in multiple roles
     *
     * @param emlElement The EML contact element
     * @param dataResource The DataResource this contact belongs to
     * @return The created or updated Contact, or null if invalid
     */
    private def addOrUpdateContact(emlElement, DataResource dataResource) {
        // Extract contact details from EML
        String firstName = emlElement.individualName?.givenName?.text()?.trim() ?: null
        String lastName = emlElement.individualName?.surName?.text()?.trim() ?: null
        String email = emlElement.electronicMailAddress?.text()?.trim() ?: null
        String phone = emlElement.phone?.text()?.trim() ?: null
        String organizationName = emlElement.organizationName?.text()?.trim() ?: null
        String positionName = emlElement.positionName?.text()?.trim() ?: null
        String userId = emlElement.userId?.text()?.trim()
        String userIdDirectory = emlElement.userId?.@directory?.text()?.trim()
        String userIdUrl = userIdDirectory && userId ? "${userIdDirectory}${userId}" : null

        // Validation: must have at least surname, org, or position
        boolean hasSurName = lastName != null && !lastName.isEmpty()
        boolean hasOrg = organizationName != null && !organizationName.isEmpty()
        boolean hasPosition = positionName != null && !positionName.isEmpty()

        if (!hasSurName && !hasOrg && !hasPosition) {
            return null
        }

        Contact contact = null

        // Try to find existing contact ONLY among those already associated with this DataResource
        if (dataResource?.uid) {
            def existingContactFors = ContactFor.findAllByEntityUid(dataResource.uid)

            for (contactFor in existingContactFors) {
                Contact candidate = contactFor.contact

                // Match based on multiple fields for exactness
                boolean matches = true

                // Check lastName (required if present)
                if (hasSurName && candidate.lastName != lastName) {
                    matches = false
                }

                // Check firstName if present in both
                if (matches && firstName && candidate.firstName != firstName) {
                    matches = false
                }

                // Check email if present in both
                if (matches && email && candidate.email && candidate.email != email) {
                    matches = false
                }

                // Check organization if present in both (case-insensitive for flexibility)
                if (matches && organizationName && candidate.organizationName) {
                    if (organizationName.toLowerCase() != candidate.organizationName.toLowerCase()) {
                        matches = false
                    }
                }

                // Check position if present in both (case-insensitive for flexibility)
                if (matches && positionName && candidate.positionName) {
                    if (positionName.toLowerCase() != candidate.positionName.toLowerCase()) {
                        matches = false
                    }
                }

                // Check userId if present in both
                if (matches && userIdUrl && candidate.userId && candidate.userId != userIdUrl) {
                    matches = false
                }

                if (matches) {
                    contact = candidate
                    break
                }
            }
        }

        if (!contact) {
            // Create new contact - not associated with this resource yet
            contact = new Contact()
            contact.firstName = firstName
            contact.lastName = lastName
            contact.organizationName = organizationName
            contact.positionName = positionName
            contact.userId = userIdUrl
            contact.email = email
            contact.phone = phone
            contact.publish = true  // Default for new contacts
            contact.setUserLastModified(collectoryAuthService.username())

            Contact.withTransaction {
                if (contact.validate()) {
                    contact.save(flush: true, failOnError: true)
                } else {
                    log.error("Validation errors creating contact: ${contact.errors}")
                    return null
                }
            }
        } else {
            // Update existing contact fields if they differ
            // IMPORTANT: Preserve the 'publish' flag - user may have set it manually
            boolean updated = false
            boolean originalPublish = contact.publish  // Preserve this

            if (phone && phone != contact.phone) {
                contact.phone = phone
                updated = true
            }
            if (email && email != contact.email) {
                contact.email = email
                updated = true
            }
            if (firstName && firstName != contact.firstName) {
                contact.firstName = firstName
                updated = true
            }
            if (lastName && lastName != contact.lastName) {
                contact.lastName = lastName
                updated = true
            }
            if (organizationName && organizationName != contact.organizationName) {
                contact.organizationName = organizationName
                updated = true
            }
            if (positionName && positionName != contact.positionName) {
                contact.positionName = positionName
                updated = true
            }
            if (userIdUrl && userIdUrl != contact.userId) {
                contact.userId = userIdUrl
                updated = true
            }

            if (updated) {
                contact.publish = originalPublish  // Restore preserved flag
                contact.setUserLastModified(collectoryAuthService.username())
                Contact.withTransaction {
                    if (contact.validate()) {
                        contact.save(flush: true, failOnError: true)
                    } else {
                        log.error("Validation errors updating contact: ${contact.errors}")
                        return null
                    }
                }
            }
        }
        return contact
    }

}
