/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  void addActionListener(@NotNull final ActionListener listener) {
    myLocationField.addActionListener(listener);
  }

  /**
   * @param runnable to be called when text in textfield changed
   */
  void addTextChangeListener(@NotNull final Runnable runnable) {
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
