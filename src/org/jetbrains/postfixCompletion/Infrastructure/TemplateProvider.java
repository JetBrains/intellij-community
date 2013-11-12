package org.jetbrains.postfixCompletion.Infrastructure;

import org.jetbrains.annotations.*;

import java.lang.annotation.*;

@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface TemplateProvider {
  @NotNull String templateName();
  @NotNull String description();
  @NotNull String example();
  boolean worksOnTypes() default false;
}
