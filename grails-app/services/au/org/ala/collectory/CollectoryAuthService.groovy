package au.org.ala.collectory

import au.org.ala.web.AuthService
import au.org.ala.ws.security.client.AlaAuthClient
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.request.RequestContextHolder
import javax.servlet.http.HttpServletRequest

/**
 * Require TokenInterceptor to authenticate token and inject authorisation profile into the request for M2M call
 */
class CollectoryAuthService{
    static transactional = false
    def grailsApplication
    def providerGroupService
    AuthService authService

    @Autowired(required = false)
    AlaAuthClient alaAuthClient

    /**
     * @return full name of the user, or 'not available' if not authenticated
     */
    String username() {
        def username = authService.getDisplayName()
        return username ?: 'not available'
    }

    String userEmail() {
        String email = authService.getEmail()
        return email
    }

    def isAdmin() {
        def request = getRequest()
        return request?.isUserInRole(grailsApplication.config.ROLE_ADMIN as String)
    }
    
    /**
     * ONLY used for user interface, not for M2M calls.
     * @param role
     * @return
     */
    protected boolean userInRole(String role) {
        def roleFlag = false
        if(!grailsApplication.config.security.oidc.enabled.toBoolean()) {
            roleFlag = true
        }

        return roleFlag || request?.isUserInRole(role) || isAdmin()
    }

    /**
     * Checks if the user has the specified role and/or scope if it is a M2M request.
     *
     * If scopes are provided, it checks if the token is authorised with any of those scopes.
     * If scopes contain "*", it returns true if the token is valid.
     * If no scopes are provided, the request will be denied.
     *
     * @param [scope] scopes
     */
    private isTokenAuthorised(String[] scopes) {
        def request = getRequest()
        def isAuthorised = false
        if (scopes && scopes.size() > 0) {
            if (scopes.contains("*")) {
                isAuthorised = true
            } else {
                isAuthorised = scopes.any { scope ->
                    request?.isUserInRole(scope)
                }
            }
        } else {
            // If scopes are empty or null, deny
            isAuthorised = false
        }

        return isAuthorised
    }

    private isUserAuthorised(String[] roles) {
        def request = getRequest()
        def isAuthorised = roles.any { scope ->
            request?.isUserInRole(scope)
        }
        return isAuthorised
    }

    /**
     * Checks if the user has the specified roles.
     * or M2M token is authorised with the specified scopes.
     *
     * If roles are provided, it checks if the user has any of those roles.
     * If scopes are provided, it checks if the token is authorised with any of those scopes.
     * If scopes contain "*", it returns true if the token is valid.
     * If no scopes are provided, the request will be denied.
     *
     * @param roles
     * @param scopes
     * @return true if the user is authorised by either roles or scopes
     */
    def isAuthorised(String[] roles, String[] scopes) {
        boolean isUserAuthed = false
        if (roles) {
            isUserAuthed = isUserAuthorised(resolveRoles(roles))
        }

        def isTokenAuthed = isTokenAuthorised(resolveRoles(scopes))

        return isUserAuthed || isTokenAuthed
    }

    /**
     * Returns a list of entities that the specified user is authorised to edit.
     *
     * Note that more than one contact may correspond to the user's email address. In this
     * case, the result is a union of the lists for each contact.
     *
     * @param email
     * @return a map holding entities, a list of their uids and the latest modified date
     */
    def authorisedForUser(String email) {
        def contacts = Contact.findAllByEmail(email)
        switch (contacts.size()) {
            case 0: return [sorted: [], keys: [], latestMod: null]
            case 1: return authorisedForUser(contacts[0])
            default:
                def result = [sorted: [], keys: [], latestMod: null]
                contacts.each {
                    def oneResult = authorisedForUser(it)
                    result.sorted += oneResult.sorted
                    result.keys += oneResult.keys
                    if (oneResult.latestMod > result.latestMod) { result.latestMod = oneResult.latestMod }
                }
                return result
        }
    }

    /**
     * Returns a list of entities that the specified contact is authorised to edit.
     *
     * @param contact
     * @return a map holding entities, a list of their uids and the latest modified date
     */
    def authorisedForUser(Contact contact) {
        // get list of contact relationships
        def latestMod = null
        def entities = [:]  // map by uid to remove duplicates
        ContactFor.findAllByContact(contact).each {
            if (it.administrator) {
                def pg = providerGroupService._get(it.entityUid)
                if (pg) {
                    entities.put it.entityUid, [uid: pg.uid, name: pg.name]
                    if (it.dateLastModified > latestMod) { latestMod = it.dateLastModified }
                }
                // add children
                pg.children().each { child ->
                    // children() now seems to return some internal class resources
                    // so make sure they are PGs
                    if (child instanceof ProviderGroup) {
                        def ch = providerGroupService._get(child.uid)
                        if (ch) {
                            entities.put ch.uid, [uid: ch.uid, name: ch.name]
                        }
                    }
                }
            }
        }
        return [sorted: entities.values().sort { it.name }, keys:entities.keySet().sort(), latestMod: latestMod]
    }

    /**
     * Resolve roles/scopes from the configuration properties.
     * If a role/scope is not found in the config, it returns the role/scope as is.
     *
     * scopes/roles in config supports multiple values separated by commas or semicolons.
     *
     * LIMITATION: The check will pass if any role/scope is matched
     *
     * @param rolesOrScopes Array of role/scopes keys to resolve
     * @return Array of resolved roles/scopes
     */
    String[] resolveRoles(String[] rolesOrScopes) {
        return rolesOrScopes
                .findAll() // Remove nulls and empty strings
                .collectMany { key ->
                    def value = grailsApplication.config.getProperty(key, String, key)
                    value.split(/[;,]/)*.trim()
                }
                .findAll() // Remove empty strings from splits
                .toSet() // Remove duplicates
                .toArray(new String[0])
    }

    private HttpServletRequest getRequest() {
        def webRequest = RequestContextHolder.currentRequestAttributes() as GrailsWebRequest
        return webRequest.getCurrentRequest()
    }
}
