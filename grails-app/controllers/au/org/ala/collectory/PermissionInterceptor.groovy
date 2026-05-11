package au.org.ala.collectory

import au.org.ala.PermissionRequired
import au.org.ala.SkipPermissionCheck
import au.org.ala.grails.AnnotationMatcher
import au.org.ala.ws.security.JwtProperties
import grails.core.GrailsApplication
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.tuple.Pair
import org.pac4j.core.client.DirectClient
import org.pac4j.core.config.Config
import org.pac4j.core.context.CallContext
import org.pac4j.core.credentials.Credentials
import org.pac4j.core.exception.CredentialsException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties

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
@CompileStatic
@Slf4j
@EnableConfigurationProperties(JwtProperties)
class PermissionInterceptor {
    // Run after the Token has been validated, but before the action is executed
    int order = 100
    GrailsApplication grailsApplication
    CollectoryAuthService collectoryAuthService

    @Autowired(required = false)
    @Qualifier('alaClient')
    List<DirectClient> clientList

    @Autowired(required = false)
    Config config

    @Autowired(required = false)
    JwtProperties jwtProperties

    PermissionInterceptor() {
        matchAll()
    }

    /**
     * @ref TokenInterceptor Inject user profile and credentials into the request before the action executes if a JWT token is present.
     */
    boolean before() {
        def matchResult = AnnotationMatcher.getAnnotation(grailsApplication, controllerNamespace, controllerName, actionName, PermissionRequired, SkipPermissionCheck)
        def effectiveAnnotation = matchResult.effectiveAnnotation()
        def skipAnnotation = matchResult.overrideAnnotation

        if (effectiveAnnotation && !skipAnnotation) {
            boolean isAuthorised = false
            // user signed in or M2M token is valid
            if (request.getUserPrincipal()) {
                isAuthorised = collectoryAuthService.isAuthorised(effectiveAnnotation.roles(), effectiveAnnotation.scopes())
            }

            if (!isAuthorised) {
                if (isApiRequest()) {
                    render status: 403, contentType: 'application/json', text: '{"error": "Access denied. You do not have permissions"}'
                } else {
                    flash.message = "You do not have permission to access this resource."
                    redirect(controller: 'public', action: 'map')
                }
            }
            return isAuthorised
        }
        return true
    }


    /**
     * Executed after the action executes but prior to view rendering
     *
     * @return True if view rendering should continue, false otherwise
     */
    boolean after() { true }

    /**
     * Executed after view rendering completes
     */
    void afterView() {}

    private boolean isApiRequest() {
        def req = request

        // Check Accept header
        if (req.getHeader('Accept')?.contains('application/json')) {
            return true
        }

        // Check request format
        if (request.format in ['json', 'xml']) {
            return true
        }

        // Check URL path (optional)
        if (request.forwardURI?.contains('/ws/') || request.forwardURI?.contains('/api/')) {
            return true
        }

        // Check if it's an AJAX call
        if (request.xhr) {
            return true
        }

        return false
    }


    // This is a condensed version of the pac4j DefaultSecurityLogic, we don't need the full logic here
    // as we are only interested in the credentials and scopes.
    Optional<Pair<DirectClient, Credentials>> getCredentials(List<DirectClient> clients, CallContext context) {
        try {
            for (DirectClient client : clients) {
                Credentials credentials = client.getCredentials(context).orElse(null)
                credentials = (Credentials) client.validateCredentials(context, credentials).orElse(null)
                if (credentials != null && credentials.isForAuthentication()) {
                    return Optional.of(Pair.of(client, credentials))
                }
            }
        } catch (CredentialsException e) {
            log.info("Failed to retrieve credentials: {}", e.getMessage())
            log.debug("Failed to retrieve credentials", e)
        }
        return Optional.empty()
    }
}