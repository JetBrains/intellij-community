
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector;


public class InplaceContext {
  private final boolean myKeepInitialValue;
  private boolean myStartedByTyping;
  private char myStartChar;
  private boolean myModalDialogDisplayed;

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

  public boolean isModalDialogDisplayed() {
    return myModalDialogDisplayed;
  }

  public void setModalDialogDisplayed(boolean modalDialogDisplayed) {
    myModalDialogDisplayed = modalDialogDisplayed;
  }
}
