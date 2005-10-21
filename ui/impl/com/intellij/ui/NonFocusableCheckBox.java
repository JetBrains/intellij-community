package com.intellij.ui;

import javax.swing.*;

/**
 *  @author dsl
 */
public class NonFocusableCheckBox extends JCheckBox {
  public NonFocusableCheckBox(String text) {
    super(text);
    setFocusable(false);
  }

  public NonFocusableCheckBox() {
    setFocusable(false);
  }
}
