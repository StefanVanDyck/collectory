package au.org.ala.collectory

import au.org.ala.PermissionRequired

@PermissionRequired(roles=['ROLE_EDITOR', 'ROLE_ADMIN'])
class DataHubController extends ProviderGroupController {

    def grailsCacheAdminService

    DataHubController() {
        entityName = "DataHub"
        entityNameLower = "dataHub"
    }

    def index = {
        redirect(action:"list")
    }

    // list all entities
    def list = {
        if (params.message)
            flash.message = params.message
        params.max = Math.min(params.max ? params.int('max') : 10, 100)
        params.sort = params.sort ?: "name"
        activityLogService.log username(), isAdmin(), Action.LIST
        [instanceList: DataHub.list(params), entityType: 'DataHub', instanceTotal: DataHub.count()]
    }

    def show = {
        // After updating members this page is shown
        grailsCacheAdminService.clearCache('dataHubCache')

        def instance = get(params.id)
        if (!instance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'dataHub.label', default: 'Data Hub'), params.id])}"
            redirect(action: "list")
        }
        else {
            log.debug "Ala partner = " + instance.isALAPartner
            activityLogService.log username(), isAdmin(), instance.uid, Action.VIEW

            [instance: instance, contacts: instance.getContacts(), changes: getChanges(instance.uid)]
        }
    }

    def delete = {
        grailsCacheAdminService.clearCache('dataHubCache')

        def instance = get(params.id)
        if (instance) {
            if (isAdmin()) {
                // remove contact links (does not remove the contact)
                ContactFor.findAllByEntityUid(instance.uid).each {
                    it.delete(flush: true)
                }
                // now delete
                try {
                    activityLogService.log username(), isAdmin(), params.id as long, Action.DELETE
                    DataHub.withTransaction {
                        instance.delete(flush: true)
                    }
                    flash.message = "${message(code: 'default.deleted.message', args: [message(code: 'dataHub.label', default: 'Data Hub'), params.id])}"
                    redirect(action: "list")
                }
                catch (org.springframework.dao.DataIntegrityViolationException e) {
                    flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: 'dataHub.label', default: 'Data Hub'), params.id])}"
                    redirect(action: "show", id: params.id)
                }
            } else {
                render("You are not authorised to access this page.")
            }
        }
        else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'dataHub.label', default: 'Data Hub'), params.id])}"
            redirect(action: "list")
        }
    }

    /**
     * Get the instance for this entity based on either uid or DB id.
     *
     * @param id UID or DB id
     * @return the entity of null if not found
     */
    protected ProviderGroup get(id) {
        if (id.size() > 2) {
            if (id[0..1] == DataHub.ENTITY_PREFIX) {
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
        return DataHub.get(dbId)
    }
}
