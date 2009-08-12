package com.intellij.openapi.vcs;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Used to indicate that a method (for instance, of version control plugin provider interface)
 * would be called in background thread
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD})
public @interface CalledInBackground {
}
