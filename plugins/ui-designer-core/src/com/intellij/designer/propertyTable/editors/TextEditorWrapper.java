// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.propertyTable.editors;

import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.propertyTable.InplaceContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author Alexander Lobas
 */
public class TextEditorWrapper extends TextEditor {
  private final JComponent myComponent;

  public TextEditorWrapper() {
    myComponent = new JComponent() {
      @Override
      public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        myTextField.setBounds(0, 0, width, height);
      }

      @Override
      public void setBackground(Color bg) {
        super.setBackground(bg);
        myTextField.setBackground(bg);
      }

      @Override
      public void setForeground(Color fg) {
        super.setForeground(fg);
        myTextField.setForeground(fg);
      }

      @Override
      public Dimension getPreferredSize() {
        return myTextField.getPreferredSize();
      }
    };
    myComponent.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myComponent.setFocusable(false);
    myComponent.add(myTextField);
  }

  @Override
  public @NotNull JComponent getComponent(@Nullable PropertiesContainer container,
                                          @Nullable PropertyContext context,
                                          Object value,
                                          @Nullable InplaceContext inplaceContext) {
    super.getComponent(container, context, value, inplaceContext);
    return myComponent;
  }
}