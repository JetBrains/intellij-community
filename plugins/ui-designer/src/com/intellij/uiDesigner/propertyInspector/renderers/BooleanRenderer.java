// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

public class BooleanRenderer extends JCheckBox implements PropertyRenderer<Boolean> {
  @Override
  public JComponent getComponent(final RadRootContainer rootContainer, final Boolean value, final boolean selected, final boolean hasFocus){
    // Background and foreground
    if(selected){
      setForeground(UIUtil.getTableSelectionForeground(true));
      setBackground(UIUtil.getTableSelectionBackground(true));
    }else{
      setForeground(UIUtil.getTableForeground());
      setBackground(UIUtil.getTableBackground());
    }

    setSelected(value != null && value.booleanValue());

    return this;
  }
}