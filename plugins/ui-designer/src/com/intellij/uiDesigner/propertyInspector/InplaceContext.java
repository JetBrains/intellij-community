/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector;

/**
 * @author yole
 */
public class InplaceContext {
  private final boolean myKeepInitialValue;
  private boolean myStartedByTyping;
  private char myStartChar;

  public InplaceContext(boolean keepInitialValue) {
    myKeepInitialValue = keepInitialValue;
  }

  public InplaceContext(boolean keepInitialValue, final char startChar) {
    myKeepInitialValue = keepInitialValue;
    myStartedByTyping = true;
    myStartChar = startChar;
  }

  public boolean isKeepInitialValue() {
    return myKeepInitialValue;
  }

  public boolean isStartedByTyping() {
    return myStartedByTyping;
  }

  public char getStartChar() {
    return myStartChar;
  }
}
