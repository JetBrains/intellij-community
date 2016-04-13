package de.plushnikov.intellij.plugin.agent.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Alexej Kubarev
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SupportedBuild {
  /**
   * Minumum supported version.
   */
  String min() default "";

  /**
   * Maximum supported version.
   */
  String max() default "";
}
