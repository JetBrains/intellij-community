package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class BooleanRenderer extends JCheckBox implements PropertyRenderer{
  public JComponent getComponent(final Object value, final boolean selected, final boolean hasFocus){
    // Background and foreground
    if(selected){
      setForeground(UIManager.getColor("Table.selectionForeground"));
      setBackground(UIManager.getColor("Table.selectionBackground"));
    }else{
      setForeground(UIManager.getColor("Table.foreground"));
      setBackground(UIManager.getColor("Table.background"));
    }

    setSelected(((Boolean)value).booleanValue());

    return this;
  }
}