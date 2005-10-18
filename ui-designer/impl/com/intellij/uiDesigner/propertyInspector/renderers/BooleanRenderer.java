package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BooleanRenderer extends JCheckBox implements PropertyRenderer{
  public JComponent getComponent(final Object value, final boolean selected, final boolean hasFocus){
    // Background and foreground
    if(selected){
      setForeground(UIUtil.getTableSelectionForeground());
      setBackground(UIUtil.getTableSelectionBackground());
    }else{
      setForeground(UIUtil.getTableForeground());
      setBackground(UIUtil.getTableBackground());
    }

    setSelected(((Boolean)value).booleanValue());

    return this;
  }
}