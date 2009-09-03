package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class BooleanRenderer extends JCheckBox implements PropertyRenderer<Boolean> {
  public JComponent getComponent(final RadRootContainer rootContainer, final Boolean value, final boolean selected, final boolean hasFocus){
    // Background and foreground
    if(selected){
      setForeground(UIUtil.getTableSelectionForeground());
      setBackground(UIUtil.getTableSelectionBackground());
    }else{
      setForeground(UIUtil.getTableForeground());
      setBackground(UIUtil.getTableBackground());
    }

    setSelected(value != null && value.booleanValue());

    return this;
  }
}