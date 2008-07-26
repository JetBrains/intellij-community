package org.jetbrains.plugins.ruby.support;

import java.util.EmptyStackException;

/**
 * @author Roman Chernyatchik
 */
public abstract class EmptyStackExceptionCase extends AbstractExceptionCase {
  public Class<? extends Exception> getExpectedExceptionClass() {
    return EmptyStackException.class;
  }
}
