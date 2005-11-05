/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui.popup;

import javax.swing.*;

public class ListSeparator {

  private String myText;
  private Icon myIcon;

  public ListSeparator() {
    this("");
  }

  public ListSeparator(String aText) {
    this(aText, null);
  }

  public ListSeparator(String name, Icon icon) {
    myText = name;
    myIcon = icon;
  }

  public String getText() {
    return myText;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
