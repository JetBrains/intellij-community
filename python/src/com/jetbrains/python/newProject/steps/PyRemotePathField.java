// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.steps;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.TextAccessor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionListener;

/**
 * Field for remote path with "browse" button
 *
 * @author Ilya.Kazakevich
 */
public final class PyRemotePathField {
  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myLocationField;

  @NotNull
  JPanel getMainPanel() {
    return myMainPanel;
  }

  void setReadOnly(final boolean readOnly) {
    myLocationField.setEditable(! readOnly);
    myLocationField.getButton().setVisible(! readOnly);
  }

  /**
   * Add listener for "browse" button
   */
  void addActionListener(final @NotNull ActionListener listener) {
    myLocationField.addActionListener(listener);
  }

  /**
   * @param runnable to be called when text in textfield changed
   */
  void addTextChangeListener(final @NotNull Runnable runnable) {
    myLocationField.getTextField().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        runnable.run();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        runnable.run();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        runnable.run();
      }
    });
  }
  /**
   * @return test field with remote path
   */
  @NotNull
  TextAccessor getTextField() {
    return myLocationField;
  }
}
