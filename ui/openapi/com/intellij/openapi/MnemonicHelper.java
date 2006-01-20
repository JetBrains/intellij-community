/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi;

import com.intellij.ui.ComponentTreeWatcher;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Automatically locates &amp; characters in texts of buttons and labels on a component or dialog,
 * registers the mnemonics for those characters and removes them from the control text.
 *
 * @author lesya
 * @since 5.1
 */
public class MnemonicHelper extends ComponentTreeWatcher {
  public static final PropertyChangeListener TEXT_LISTENER = new PropertyChangeListener() {
   public void propertyChange(PropertyChangeEvent evt) {
     final Object source = evt.getSource();
     if (source instanceof AbstractButton) {
       DialogUtil.registerMnemonic(((AbstractButton)source));
     } else if (source instanceof JLabel) {
       DialogUtil.registerMnemonic(((JLabel)source), null);
     }
   }
  };
  @NonNls public static final String TEXT_CHANGED_PROPERTY = "text";

  public MnemonicHelper() {
    super(ArrayUtil.EMPTY_CLASS_ARRAY);
  }

  protected void processComponent(Component parentComponent) {
    if (parentComponent instanceof AbstractButton) {
      final AbstractButton abstractButton = ((AbstractButton)parentComponent);
      abstractButton.addPropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, TEXT_LISTENER);
      DialogUtil.registerMnemonic(abstractButton);
    } else if (parentComponent instanceof JLabel) {
      final JLabel jLabel = ((JLabel)parentComponent);
      jLabel.addPropertyChangeListener(TEXT_CHANGED_PROPERTY, TEXT_LISTENER);
      DialogUtil.registerMnemonic(jLabel, null);
    }
  }

  protected void unprocessComponent(Component component) {
  }
}
