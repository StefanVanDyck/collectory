package au.org.ala.collectory

import au.org.ala.ws.security.JwtProperties
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.tuple.Pair
import org.pac4j.core.adapter.FrameworkAdapter
import org.pac4j.core.client.DirectClient
import org.pac4j.core.config.Config
import org.pac4j.core.context.CallContext
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.credentials.Credentials
import org.pac4j.core.exception.CredentialsException
import org.pac4j.core.profile.ProfileManager
import org.pac4j.core.profile.UserProfile
import org.pac4j.jee.context.JEEFrameworkParameters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import javax.annotation.PostConstruct

@CompileStatic
@Slf4j
@EnableConfigurationProperties(JwtProperties)
/**
 * This interceptor ALWAYS VALIDATE JWT token and fill scopes to the user profile in request.
 * It can be removed when the new security plugin >7.0.1 is used, as it will handle the token validation and profile management.
 */
class TokenInterceptor {
    // Run before other interceptors since other interceptors may ask Token info
    int order = 0

    @Autowired(required = false)
    @Qualifier('alaClient')
    List<DirectClient> clientList

    @Autowired(required = false)
    Config config

    @Autowired(required = false)
    JwtProperties jwtProperties

    TokenInterceptor() {
        matchAll()
    }

    /**
     * Inject user profile and credentials into the request before the action executes if a JWT token is present.
     *
     * @return the request should always continue to the action,
     */
    boolean before() {
        if (request.getHeader("authorization")) {
            try {
                def params = new JEEFrameworkParameters(request, response)
                FrameworkAdapter.INSTANCE.applyDefaultSettingsIfUndefined(config)
                final WebContext context = config.getWebContextFactory().newContext(params)
                final SessionStore sessionStore = config.sessionStoreFactory.newSessionStore(params)
                final callContext = new CallContext(context, sessionStore, config.profileManagerFactory)

                Optional<Pair<DirectClient, Credentials>> optCredentials = getCredentials(clientList, callContext)
                Optional<UserProfile> optProfile = Optional.empty()

                if (optCredentials.isPresent()) {

                    def pair = optCredentials.get()
                    def client = pair.left
                    Credentials credentials = pair.right

                    if (optProfile.isEmpty()) {
                        optProfile = client.getUserProfile(callContext, credentials)
                    }

                    if (optProfile.isPresent()) {
                        UserProfile userProfile = optProfile.get()
                        ProfileManager profileManager = config.profileManagerFactory.apply(context, sessionStore)
                        profileManager.setConfig(config)
                        profileManager.save(
                                client.getSaveProfileInSession(context, userProfile),
                                userProfile,
                                client.isMultiProfile(context, userProfile)
                        )
                    } else {
                        log.debug "no user profile available missing roles"
                    }
                } else {
                    log.debug "no auth credentials found"
                }
            } catch (CredentialsException e) {
                log.info("authentication failed invalid credentials", e)
            }
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