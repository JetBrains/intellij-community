/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.exception;

public class RootRuntimeException extends RuntimeException {

  protected RootRuntimeException() {
    super();
  }

  public RootRuntimeException(String aExceptionMessage) {
    super(aExceptionMessage);
  }

  public RootRuntimeException(String aExceptionMessage,
                          Throwable aNestedException) {
    super(aExceptionMessage);
    initCause(aNestedException);
  }

  public RootRuntimeException(Throwable aNestedException) {
    super();
    initCause(aNestedException);
  }

  protected Throwable getNestedException() {
    return getCause();
  }

  protected boolean hasNestedException() {
    return null != getNestedException();
  }

}
