package au.org.ala.collectory

import au.org.ala.PermissionRequired
import grails.gorm.transactions.Transactional

@PermissionRequired(roles=['ROLE_EDITOR', 'ROLE_ADMIN'])
class ProviderMapController {

    static allowedMethods = [save: "POST", update: "POST", delete: "POST"]

    def collectoryAuthService

    /*
     * Access control
     *
     * All methods require EDITOR role.
     * Edit methods require ADMIN or the user to be an administrator for the entity.
     */

    def index = {
        redirect(action: "list", params: params)
    }

    def list = {
        if (!params.max) params.max = 10
        if (!params.offset) params.offset = 0
        if (!params.order) params.order = "asc"
        def maps = ProviderMap.withCriteria {
            maxResults(params.getInt('max'))
            firstResult(params.getInt('offset'))
        }
        [providerMapInstanceList: maps, providerMapInstanceTotal: ProviderMap.count(), returnTo: params.returnTo]
    }

    def create = {
        def providerMapInstance = new ProviderMap()
        providerMapInstance.properties = params
        if (params.createFor) {
            def pg = Collection.findByUid(params.createFor) as Collection
            if (pg) {
                providerMapInstance.collection = pg
                providerMapInstance.institution = pg.institution
            }
        }
        return [providerMapInstance: providerMapInstance, returnTo: params.returnTo]
    }

    @Transactional
    def save () {
        def providerMapInstance = new ProviderMap(params)
       if (providerMapInstance.collection && providerMapInstance.collection.providerMap){
           flash.message = "${message(code: 'providerMap.collection.used.label', args: [providerMapInstance.collection.uid])}"
           render(view: "create", model: [providerMapInstance: providerMapInstance, returnTo: params.returnTo])
           return
       }
        if (providerMapInstance.save(flush: true)) {
            flash.message = "${message(code: 'default.created.message', args: [message(code: 'providerMap.label', default: 'ProviderMap'), providerMapInstance.id])}"
            render(view: "create", model: [providerMapInstance: providerMapInstance, returnTo: params.returnTo])
        }
        else {
            render(view: "create", model: [providerMapInstance: providerMapInstance, returnTo: params.returnTo])
        }
    }

    def show = {
        def providerMapInstance = ProviderMap.get(params.id)
        if (!providerMapInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'providerMap.label', default: 'ProviderMap'), params.id])}"
            redirect(action: "list", params:[returnTo: params.returnTo])
        }
        else {
            [providerMapInstance: providerMapInstance, returnTo: params.returnTo]
        }
    }

    def edit = {
        def providerMapInstance = ProviderMap.get(params.id)
        if (!providerMapInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'providerMap.label', default: 'ProviderMap'), params.id])}"
            redirect(action: "list", params:[returnTo: params.returnTo])
        }
        else {
            if (providerMapInstance) {
                return [providerMapInstance: providerMapInstance, returnTo: params.returnTo]
            } else {
                render "You are not authorised to access this page."
            }
        }
    }

    @Transactional
    def update () {
        def providerMapInstance = ProviderMap.get(params.id)
        if (providerMapInstance) {
            if (params.version) {
                def version = params.version.toLong()
                if (providerMapInstance.version > version) {

                    providerMapInstance.errors.rejectValue("version", "default.optimistic.locking.failure", [message(code: 'providerMap.label', default: 'ProviderMap')] as Object[], "Another user has updated this ProviderMap while you were editing")
                    render(view: "edit", model: [providerMapInstance: providerMapInstance], params:[returnTo: params.returnTo])
                    return
                }
            }
            providerMapInstance.properties = params
            if (!providerMapInstance.hasErrors() && providerMapInstance.save(flush: true)) {
                flash.message = "${message(code: 'default.updated.message', args: [message(code: 'providerMap.label', default: 'ProviderMap'), providerMapInstance.id])}"
                redirect(action: "show", id: providerMapInstance.id, params:[returnTo: params.returnTo])
            }
            else {
                render(view: "edit", model: [providerMapInstance: providerMapInstance, returnTo: params.returnTo])
            }
        }
        else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'providerMap.label', default: 'ProviderMap'), params.id])}"
            redirect(action: "list", params:[returnTo: params.returnTo])
        }
    }

    @Transactional
    def delete () {
        if (collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN)) {
            def providerMapInstance = ProviderMap.get(params.id)
            if (providerMapInstance) {
                if (providerMapInstance.collection.uid) {
                    try {
                        // remove collection link
                        providerMapInstance.collection?.providerMap = null
                        // remove code links
                        providerMapInstance.collectionCodes.removeAll(providerMapInstance.collectionCodes)
                        providerMapInstance.institutionCodes.removeAll(providerMapInstance.institutionCodes)
                        // remove map
                        providerMapInstance.delete(flush: true)
                        flash.message = "${message(code: 'default.deleted.message', args: [message(code: 'providerMap.label', default: 'ProviderMap'), params.id])}"
                        redirect(action: "list", params: [returnTo: params.returnTo])
                    }
                    catch (org.springframework.dao.DataIntegrityViolationException e) {
                        flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: 'providerMap.label', default: 'ProviderMap'), params.id])}"
                        redirect(action: "show", id: params.id, params: [returnTo: params.returnTo])
                    }
                } else {
                    render "You are not authorised to access this page."
                }
            } else {
                flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'providerMap.label', default: 'ProviderMap'), params.id])}"
                redirect(action: "list", params: [returnTo: params.returnTo])
            }
        } else{
            response.setHeader("Content-type", "text/plain; charset=UTF-8")
            render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
        }
    }
}
