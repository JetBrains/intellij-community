/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.exception;



public class RootException extends Exception {

  protected RootException() {
    super();
  }

  public RootException(String aExceptionMessage) {
    super(aExceptionMessage);
  }

  public RootException(String aExceptionMessage,
                          Throwable aNestedException) {
    super(aExceptionMessage);
    initCause(aNestedException);
  }

  public RootException(Throwable aNestedException) {
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
