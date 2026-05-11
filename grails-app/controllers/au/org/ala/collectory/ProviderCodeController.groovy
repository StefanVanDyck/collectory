package au.org.ala.collectory

import au.org.ala.PermissionRequired
import grails.gorm.transactions.Transactional
import org.springframework.dao.DataIntegrityViolationException

@PermissionRequired(roles=['ROLE_EDITOR', 'ROLE_ADMIN'])
class ProviderCodeController {

    static allowedMethods = [save: "POST", update: "POST", delete: "POST"]
    def collectoryAuthService

    def index() {
        redirect(action: "list", params: params)
    }

    def list(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        [providerCodeInstanceList: ProviderCode.list(params), providerCodeInstanceTotal: ProviderCode.count()]
    }

    @Transactional
    def create() {
        [providerCodeInstance: new ProviderCode(params)]
    }

    @Transactional
    def save() {
        def providerCodeInstance = new ProviderCode(params)
        if (!providerCodeInstance.save(flush: true)) {
            render(view: "create", model: [providerCodeInstance: providerCodeInstance])
            return
        }

        flash.message = message(code: 'default.created.message', args: [message(code: 'providerCode.label', default: 'ProviderCode'), providerCodeInstance.id])
        redirect(action: "show", id: providerCodeInstance.id)
    }

    def show(Long id) {
        def providerCodeInstance = ProviderCode.get(id)
        if (!providerCodeInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'providerCode.label', default: 'ProviderCode'), id])
            redirect(action: "list")
            return
        }

        [providerCodeInstance: providerCodeInstance]
    }

    def edit(Long id) {
        def providerCodeInstance = ProviderCode.get(id)
        if (!providerCodeInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'providerCode.label', default: 'ProviderCode'), id])
            redirect(action: "list")
            return
        }

        [providerCodeInstance: providerCodeInstance]
    }

    @Transactional
    def update(Long id, Long version) {
        def providerCodeInstance = ProviderCode.get(id)
        if (!providerCodeInstance) {
            flash.message = message(code: 'default.not.found.message', args: [message(code: 'providerCode.label', default: 'ProviderCode'), id])
            redirect(action: "list")
            return
        }

        if (version != null) {
            if (providerCodeInstance.version > version) {
                providerCodeInstance.errors.rejectValue("version", "default.optimistic.locking.failure",
                        [message(code: 'providerCode.label', default: 'ProviderCode')] as Object[],
                        "Another user has updated this ProviderCode while you were editing")
                render(view: "edit", model: [providerCodeInstance: providerCodeInstance])
                return
            }
        }

        providerCodeInstance.properties = params

        if (!providerCodeInstance.save(flush: true)) {
            render(view: "edit", model: [providerCodeInstance: providerCodeInstance])
            return
        }

        flash.message = message(code: 'default.updated.message', args: [message(code: 'providerCode.label', default: 'ProviderCode'), providerCodeInstance.id])
        redirect(action: "show", id: providerCodeInstance.id)
    }

    @Transactional
    def delete(Long id) {
        if (collectoryAuthService?.userInRole(grailsApplication.config.ROLE_ADMIN)) {
            def providerCodeInstance = ProviderCode.get(id)
            if (!providerCodeInstance) {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'providerCode.label', default: 'ProviderCode'), id])
                redirect(action: "list")
                return
            }

            try {
                providerCodeInstance.delete(flush: true)
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'providerCode.label', default: 'ProviderCode'), id])
                redirect(action: "list")
            } catch (DataIntegrityViolationException e) {
                flash.message = message(code: 'default.not.deleted.message', args: [message(code: 'providerCode.label', default: 'ProviderCode'), id])
                redirect(action: "show", id: id)
            }
        } else {
            response.setHeader("Content-type", "text/plain; charset=UTF-8")
            render(message(code: "provider.group.controller.04", default: "You are not authorised to access this page."))
        }
    }
}
