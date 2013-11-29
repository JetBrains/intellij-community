package org.jetbrains.postfixCompletion.infrastructure;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface TemplateInfo {
  @NotNull String templateName();
  @NotNull String description();
  @NotNull String example();
  // todo: move behavior to org.jetbrains.postfixCompletion.templates.PostfixTemplate
  boolean worksOnTypes() default false;
  boolean worksInsideFragments() default false;
}
