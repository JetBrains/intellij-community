package com.intellij.tasks.impl.gson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for required fields, that can't hold 'null' after deserialization.
 * Standard @NotNull can't be used for this purpose, because its retention policy is CLASS.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Mandatory {
  // empty
}
