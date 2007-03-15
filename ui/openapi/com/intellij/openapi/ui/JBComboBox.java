package com.intellij.openapi.ui;

import javax.swing.*;
import java.util.Vector;
import java.awt.*;

public class JBComboBox extends JComboBox {

  private boolean myLayingOut = false;

  public JBComboBox(final ComboBoxModel aModel) {
    super(aModel);
  }

  public JBComboBox(final Object items[]) {
    super(items);
  }

  public JBComboBox(final Vector<?> items) {
    super(items);
  }

  public JBComboBox() {
  }

  public void doLayout() {
    try {
      myLayingOut = true;
      super.doLayout();
    }
    finally {
      myLayingOut = false;
    }
  }

  public Dimension getSize() {
    Dimension size = super.getSize();
    if (!myLayingOut) {
      size.width = Math.max(size.width, getOriginalPreferredSize().width);
    }
    return size;
  }

  protected Dimension getOriginalPreferredSize() {
    return getPreferredSize();
  }
}
