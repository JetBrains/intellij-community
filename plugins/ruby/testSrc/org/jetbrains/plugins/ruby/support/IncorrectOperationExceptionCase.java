package org.jetbrains.plugins.ruby.support;

import com.intellij.util.IncorrectOperationException;

/**
 * @author Roman Chernyatchik
 */
public abstract class IncorrectOperationExceptionCase extends AbstractExceptionCase<IncorrectOperationException> {
  public Class<IncorrectOperationException> getExpectedExceptionClass() {
    return IncorrectOperationException.class;
  }
}