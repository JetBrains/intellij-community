package de.plushnikov;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines that a test class may only run if the current API version is at least the given value.
 * Requires a test class to extend {@link ApiVersionAwareLightJavaCodeInsightFixtureTestCase}, as it contains conditional logic.
 * Additional base classes may be added in the same way.
 *
 * @author Alexej Kubarev
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RequiredApiVersion {

  String value();
}
