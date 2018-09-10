// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.shared.BorderType;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BorderTypeEditor extends ComboBoxPropertyEditor<BorderType> {
  public BorderTypeEditor(){
    myCbx.setModel(new DefaultComboBoxModel(BorderType.getAllTypes()));
    myCbx.setRenderer(new ListCellRendererWrapper<BorderType>() {
      @Override
      public void customize(JList list, BorderType value, int index, boolean selected, boolean hasFocus) {
        final BorderType type = value != null ? value : BorderType.NONE;
        setText(type.getName());
      }
    });
  }

  @Override
  public JComponent getComponent(final RadComponent ignored, final BorderType value, final InplaceContext inplaceContext){
    myCbx.setSelectedItem(value);
    return myCbx;
  }
}
