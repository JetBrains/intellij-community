package org.jetbrains.plugins.ruby.support;

/**
 * @author Roman Chernyatchik
 *
 * Base class of block, annotated with exception. Inheritors of this
 * class specifies concrete Exception classes
 */
public abstract class AbstractExceptionCase {

    public abstract Class<? extends Exception> getExpectedExceptionClass();

  /**
   * Suspicious code must be in implementation of this closure
   */
    public abstract void tryClosure();

    public String getAssertionErrorMessage() {
        return getExpectedExceptionClass().getName() + " must be thrown.";
    }
}
