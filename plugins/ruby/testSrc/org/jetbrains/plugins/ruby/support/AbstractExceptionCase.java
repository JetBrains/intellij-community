package org.jetbrains.plugins.ruby.support;

/**
 * @author Roman Chernyatchik
 *
 * Base class of block, annotated with exception. Inheritors of this
 * class specifies concrete Exception classes
 */
public abstract class AbstractExceptionCase<T extends Throwable> {

    public abstract Class<T> getExpectedExceptionClass();

  /**
   * Suspicious code must be in implementation of this closure
   * @throws T
   */
    public abstract void tryClosure() throws T;

    public String getAssertionErrorMessage() {
        return getExpectedExceptionClass().getName() + " must be thrown.";
    }
}
