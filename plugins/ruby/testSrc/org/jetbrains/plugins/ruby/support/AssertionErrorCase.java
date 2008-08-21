package org.jetbrains.plugins.ruby.support;

/**
 * @author Roman Chernyatchik
 */
public abstract class AssertionErrorCase extends AbstractExceptionCase {
  public Class<? extends Throwable> getExpectedExceptionClass() {
    return AssertionError.class;
  }
}