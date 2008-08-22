package org.jetbrains.plugins.ruby.support;

/**
 * @author Roman Chernyatchik
 */
public abstract class AssertionErrorCase extends AbstractExceptionCase<AssertionError> {
  public Class<AssertionError> getExpectedExceptionClass() {
    return AssertionError.class;
  }
}