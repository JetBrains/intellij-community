package org.jetbrains.plugins.ruby.support;

/**
 * @author: Dennis.Ushakov
 */
public abstract class IllegalArgumentExceptionCase extends AbstractExceptionCase<IllegalArgumentException>{
  public Class<IllegalArgumentException> getExpectedExceptionClass() {
    return IllegalArgumentException.class;
  }
}
