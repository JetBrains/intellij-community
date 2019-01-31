// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    @Override
    public void insertUpdate(DocumentEvent documentEvent) {
      updateOkButton();
    }

    @Override
    public void removeUpdate(DocumentEvent documentEvent) {
      updateOkButton();
    }

    @Override
    public void changedUpdate(DocumentEvent documentEvent) {
      updateOkButton();
    }
  }

  protected void addUpdater(JTextField field) {
    field.getDocument().addDocumentListener(new MyDocumentListener());
  }

  protected void addUpdater(JToggleButton check) {
    check.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        updateOkButton();
      }
    });
  }
}
