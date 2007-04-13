/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.util;

import java.awt.event.KeyEvent;

public abstract class SpeedSearch {

  private String myString = "";
  private boolean myEnabled;

  public void type(String letter) {
    myString += letter;
  }

  public void backspace() {
    if (myString.length() > 0) {
      myString = myString.substring(0, myString.length() - 1);
    }
  }

  public boolean shouldBeShowing(String string) {
    if (string == null) return true;
    return string.toUpperCase().indexOf(myString.toUpperCase()) >= 0;
  }

  public void process(KeyEvent e) {
    String old = myString;

    if (e.isConsumed()) return;

    if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
      backspace();
    }
    else if (Character.isLetterOrDigit(e.getKeyChar())) {
      type(Character.toString(e.getKeyChar()));
    }

    if (!old.equalsIgnoreCase(myString)) {
      update();
    }
  }

  protected abstract void update();

  public boolean isHoldingFilter() {
    return myEnabled && myString.length() > 0;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void reset() {
    if (isHoldingFilter()) {
      myString = "";
    }

    if (myEnabled) {
      update();
    }
  }

  public String getFilter() {
    return myString;
  }
}
