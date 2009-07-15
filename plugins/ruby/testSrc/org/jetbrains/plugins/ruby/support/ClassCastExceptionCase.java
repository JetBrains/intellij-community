package org.jetbrains.plugins.ruby.support;

/**
 * @author: Dennis.Ushakov
 */
public abstract class ClassCastExceptionCase extends AbstractExceptionCase<ClassCastException>{
  public Class<ClassCastException> getExpectedExceptionClass() {
    return ClassCastException.class;
  }
}