package au.org.ala

import java.lang.annotation.*

/**
 *
 * Annotation to validate the token and user login info in the request
 *
 * If scopes are empty, it only requires a valid token.
 */
@Target([ElementType.TYPE, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface PermissionRequired {
    /** roles in config support multiple values separated by commas or semicolons.
     *
     */
    String[] roles() default []
    /**
     * Only taken into account for JWT authentications.  Combined with security.jwt.scopes
     * scopes in config support multiple values separated by commas or semicolons.
     *
     * If scopes are empty, it DENIES the request.
     * If scopes contain "*", it allows the request if the token is valid.
     * otherwise, it checks if the scopes.
     * @return
     */
    String[] scopes() default []
}