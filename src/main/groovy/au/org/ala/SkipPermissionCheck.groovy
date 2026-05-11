package au.org.ala

import java.lang.annotation.*

/**
 * Annotation to skip the check for permission. This annotation can be used to exclude specific actions when
 * {@link PermissionRequired} has been specified at the class level because the majority of actions require the key.
 */
@Target([ElementType.TYPE, ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface SkipPermissionCheck {

}