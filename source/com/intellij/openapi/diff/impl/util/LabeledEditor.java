package com.intellij.openapi.diff.impl.util;

import javax.swing.*;
import java.awt.*;

public class LabeledEditor extends JPanel {
  private final JLabel myLabel = new JLabel();

  public LabeledEditor() {
    super(new BorderLayout());
  }

  private String addReadOnly(String title, boolean readonly) {
    if (readonly) title += " (Read-only)";
    return title;
  }

  public void setComponent(JComponent component, String title) {
    removeAll();
    add(component, BorderLayout.CENTER);
    add(myLabel, BorderLayout.NORTH);
    setLabelTitle(title);
    revalidate();
  }

  private void setLabelTitle(String title) {
    myLabel.setText(title);
    myLabel.setToolTipText(title);
  }

  public void updateTitle(String title, boolean readonly) {
    setLabelTitle(addReadOnly(title, readonly));
  }
}
