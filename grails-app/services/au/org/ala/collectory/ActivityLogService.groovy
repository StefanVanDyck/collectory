package au.org.ala.collectory


class ActivityLogService {

    def providerGroupService

    def log(params) {
        def al = new ActivityLog(params)
        al.timestamp = new Date()
        ActivityLog.withTransaction {
            al.save(flush: true)
        }
    }

    /**
     * Logs a simple action by a user, eg login, logout.
     * @param user
     * @param action
     */
    def log(String user, boolean isAdmin, Action action) {
        //def a = Actions.valueOf(Actions.class, action)
        //def actionText = a ? a.toString() : action
        def al = new ActivityLog(timestamp: new Date(), user: user, admin: isAdmin, action: action.toString())
        ActivityLog.withTransaction {
            al.save(flush: true)
        }
    }

    /**
     * Logs an action that is not associated with a database entity, eg list all.
     * @param user
     * @param action
     * @param item
     */
    def log(String user, boolean isAdmin, Action action, String item) {
        def al = new ActivityLog(timestamp: new Date(), user: user, admin: isAdmin, action: "${action.toString()+' '+ item}")

        ActivityLog.withTransaction {
            al.save(flush: true, failOnError: true)
        }

    }

    /**
     * Logs an action taken on a ProviderGroup-type entity, eg create, edit.
     * @param user
     * @param uid
     * @param action
     */
    def log(String user, boolean isAdmin, String uid, Action action) {
        ProviderGroup pg = providerGroupService._get(uid)
        if (!pg) {
            log(user, isAdmin, action, " entity with uid = ${uid}")
            return
        }
        boolean isContact = false
        boolean isEntityAdmin = false
        Contact c = Contact.findByEmail(user)
        if (c) {
            ContactFor cf = ContactFor.findByContactAndEntityUid(c, uid)
            if (cf) {
                isContact = true
                isEntityAdmin = cf.isAdministrator()
            }
        }
        ActivityLog.withTransaction {
            new ActivityLog(timestamp: new Date(), user: user, admin: isAdmin,
                    entityUid: uid, contactForEntity: isContact,
                    administratorForEntity: isEntityAdmin, action: action.toString()).save(flush: true)
        }
    }

    /**
     * This form is used for logging actions on non-ProviderGroup types such as Contact
     * @param user the user making the change
     * @param id the db id of the contact
     * @param action the action taken
     */
    def log(String user, boolean isAdmin, long id, Action action) {
        ActivityLog.withTransaction {
            new ActivityLog(timestamp: new Date(), user: user, admin: isAdmin,
                    entityUid: id as String, action: action.toString()).save(flush: true)
        }
    }
}
