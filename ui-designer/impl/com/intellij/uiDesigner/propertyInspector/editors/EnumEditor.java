/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;

import javax.swing.*;

/**
 * @author yole
 */
public class EnumEditor extends PropertyEditor<Enum> {
  private final JComboBox myCbx;

  public EnumEditor(final Class enumClass) {
    myCbx = new JComboBox(enumClass.getEnumConstants());
    myCbx.setBorder(BorderFactory.createEmptyBorder());
  }

  public Enum getValue() throws Exception {
    return (Enum) myCbx.getSelectedItem();
  }

  public JComponent getComponent(final RadComponent component, final Enum value, final boolean inplace) {
    final ComboBoxModel model = myCbx.getModel();
    for (int i = model.getSize() - 1; i >= 0; i--) {
      if (model.getElementAt(i) == value) {
        myCbx.setSelectedIndex(i);
        return myCbx;
      }
    }
    throw new IllegalArgumentException("unknown value: " + value);
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCbx);
    SwingUtilities.updateComponentTreeUI((JComponent)myCbx.getRenderer());
  }
}
