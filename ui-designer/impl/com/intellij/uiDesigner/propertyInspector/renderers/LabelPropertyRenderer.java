package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * This is convenient class for implementing property renderers which
 * are based on JLabel.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class LabelPropertyRenderer<V> extends JLabel implements PropertyRenderer<V> {
  private String myStaticText;

  public LabelPropertyRenderer() {
    setOpaque(true);
  }

  public LabelPropertyRenderer(String staticText) {
    this();
    myStaticText = staticText;
  }

  public JLabel getComponent(final RadRootContainer rootContainer, final V value, final boolean selected, final boolean hasFocus){
    // Reset text and icon
    setText(null);
    setIcon(null);

    // Background and foreground
    if(selected){
      setForeground(UIUtil.getTableSelectionForeground());
      setBackground(UIUtil.getTableSelectionBackground());
    }else{
      setForeground(UIUtil.getTableForeground());
      setBackground(UIUtil.getTableBackground());
    }

    if (value != null) {
      customize(value);
    }

    return this;
  }

  /**
   * Here all subclasses should customize their text, icon and other
   * attributes. Note, that background and foreground colors are already
   * set.
   */
  protected void customize(@NotNull V value) {
    setText(myStaticText != null ? myStaticText : value.toString());
  }
}