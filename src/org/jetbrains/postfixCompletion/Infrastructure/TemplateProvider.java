package org.jetbrains.postfixCompletion.Infrastructure;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value= ElementType.TYPE)
@Retention(value= RetentionPolicy.RUNTIME)
public @interface TemplateProvider {
  @NotNull String templateName();
  @NotNull String description();
  @NotNull String example();
}
