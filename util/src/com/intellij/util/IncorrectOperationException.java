/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

public class IncorrectOperationException extends Exception {
  protected String myMessage;

  public IncorrectOperationException() {
    this(null);
  }

  public IncorrectOperationException(String message) {
    super(message);
    myMessage = message;
  }

  public String getMessage(){
    return myMessage;
  }
}
