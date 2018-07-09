/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Alexei Orischenko
 */
public abstract class IdeaDialog extends DialogWrapper {

  public IdeaDialog(Project project) {
    super(project, true);
  }

  public IdeaDialog(Component owner) {
    super(owner, true);
  }

  private void updateOkButton() {
    initValidation();
  }

  private class MyDocumentListener implements DocumentListener {
    public void insertUpdate(DocumentEvent documentEvent) {
      updateOkButton();
    }

    public void removeUpdate(DocumentEvent documentEvent) {
      updateOkButton();
    }

    public void changedUpdate(DocumentEvent documentEvent) {
      updateOkButton();
    }
  }

  protected void addUpdater(JTextField field) {
    field.getDocument().addDocumentListener(new MyDocumentListener());
  }

  protected void addUpdater(JToggleButton check) {
    check.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        updateOkButton();
      }
    });
  }
}
