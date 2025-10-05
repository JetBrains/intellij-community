// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * This is convenient class for implementing property renderers which
 * are based on JLabel.
 */
public class LabelPropertyRenderer<V> extends JLabel implements PropertyRenderer<V> {
  private @Nls String myStaticText;

  public LabelPropertyRenderer() {
    setOpaque(true);
    putClientProperty("html.disable", true);
    setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
  }

  public LabelPropertyRenderer(@Nls String staticText) {
    this();
    myStaticText = staticText;
  }

  @Override
  public JLabel getComponent(final RadRootContainer rootContainer, final V value, final boolean selected, final boolean hasFocus){
    // Reset text and icon
    setText(null);
    setIcon(null);

    // Background and foreground
    if(selected){
      setForeground(UIUtil.getTableSelectionForeground(true));
      setBackground(UIUtil.getTableSelectionBackground(true));
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
    if (myStaticText == null) {
      @NlsSafe String s = value.toString();
      setText(s);
    }
    else {
      setText(myStaticText);
    }
  }
}