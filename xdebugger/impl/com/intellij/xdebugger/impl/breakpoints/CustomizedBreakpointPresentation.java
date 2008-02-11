package com.intellij.xdebugger.impl.breakpoints;

import javax.swing.*;

/**
 * @author nik
*/
public class CustomizedBreakpointPresentation {
  private Icon myIcon;
  private String myErrorMessage;

  public void setIcon(final Icon icon) {
    myIcon = icon;
  }

  public void setErrorMessage(final String errorMessage) {
    myErrorMessage = errorMessage;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getErrorMessage() {
    return myErrorMessage;
  }
}
