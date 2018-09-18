// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.InplaceContext;
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

  @Override
  public Enum getValue() throws Exception {
    return (Enum) myCbx.getSelectedItem();
  }

  @Override
  public JComponent getComponent(final RadComponent component, final Enum value, final InplaceContext inplaceContext) {
    if (value == null) {
      return myCbx;
    }
    final ComboBoxModel model = myCbx.getModel();
    for (int i = model.getSize() - 1; i >= 0; i--) {
      if (model.getElementAt(i) == value) {
        myCbx.setSelectedIndex(i);
        return myCbx;
      }
    }
    throw new IllegalArgumentException("unknown value: " + value);
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myCbx);
    SwingUtilities.updateComponentTreeUI((JComponent)myCbx.getRenderer());
  }
}
