package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;

import javax.swing.*;

/**
 * This is convenient class for implementing property renderers which
 * are based on JLabel.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class LabelPropertyRenderer extends JLabel implements PropertyRenderer{
  public LabelPropertyRenderer(){
    setOpaque(true);
  }

  public final JComponent getComponent(final Object value, final boolean selected, final boolean hasFocus){
    // Reset text and icon
    setText(null);
    setIcon(null);

    // Background and foreground
    if(selected){
      setForeground(UIManager.getColor("Table.selectionForeground"));
      setBackground(UIManager.getColor("Table.selectionBackground"));
    }else{
      setForeground(UIManager.getColor("Table.foreground"));
      setBackground(UIManager.getColor("Table.background"));
    }

    customize(value);

    return this;
  }

  /**
   * Here all subclasses should customize their text, icon and other
   * attributes. Note, that background and foreground colors are already
   * set.
   */
  protected abstract void customize(Object value);
}