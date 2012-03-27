package com.jetbrains.python.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Alexei Orischenko
 *         Date: Nov 30, 2009
 */
public abstract  class IdeaDialog extends DialogWrapper {

    public IdeaDialog(Project project) {
        super(project, true);
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

    protected void addUpdater(JCheckBox check) {
        check.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                updateOkButton();
            }
        });
    }
}
