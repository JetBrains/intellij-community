package com.intellij.ui.plaf.beg;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalComboBoxIcon;
import javax.swing.plaf.metal.MetalComboBoxUI;

public class BegComboBoxUI extends MetalComboBoxUI {
  public static ComponentUI createUI(JComponent c) {
    return new BegComboBoxUI();
  }

  protected JButton createArrowButton() {
    JButton button = new BegComboBoxButton(
      comboBox, new MetalComboBoxIcon(), comboBox.isEditable() ? true : false,
      currentValuePane, listBox
    );
    button.setFocusPainted(false);
    button.setMargin(new Insets(0, 1, 1, 3));
//    button.setMargin(new Insets(0, 0, 0, 0));
    button.setDefaultCapable(false);
    return button;
  }
}