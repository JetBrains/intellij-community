/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector;

/**
 * @author yole
 */
public class InplaceContext {
  private boolean myStartedByTyping;
  private char myStartChar;

  public InplaceContext() {
  }

  public InplaceContext(final char startChar) {
    myStartedByTyping = true;
    myStartChar = startChar;
  }

  public boolean isStartedByTyping() {
    return myStartedByTyping;
  }

  public char getStartChar() {
    return myStartChar;
  }
}