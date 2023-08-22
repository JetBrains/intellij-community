// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.Maps;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.jetbrains.python.PyBundle;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;


public class PythonDocumentationEntryEditor extends DialogWrapper {
  private JPanel myPanel;
  private JTextField myNameField;
  private JTextField myURLPatternTextField;
  private JButton myInsertButton;
  private JBList myMacroList;

  public PythonDocumentationEntryEditor(Component parent) {
    super(parent, true);
    init();
    setTitle(PyBundle.message("external.documentation.edit.documentation.url"));
    myInsertButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object text = myMacroList.getSelectedValue();
        if (text != null) {
          String macroName = text.toString();
          int pos = macroName.indexOf(" - ");
          if (pos >= 0) {
            macroName = macroName.substring(0, pos);
          }
          try {
            myURLPatternTextField.getDocument().insertString(myURLPatternTextField.getCaretPosition(), macroName, null);
          }
          catch (BadLocationException ignored) {
          }
        }
      }
    });
    myMacroList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtonEnabled();
      }
    });
    updateButtonEnabled();
  }

  private void updateButtonEnabled() {
    myInsertButton.setEnabled(myMacroList.getSelectedValue() != null);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Map.Entry<String, String> getEntry() {
    return Maps.immutableEntry(myNameField.getText(), myURLPatternTextField.getText());
  }

  public void setEntry(Map.Entry<String, String> entry) {
    myNameField.setText(entry.getKey());
    myURLPatternTextField.setText(entry.getValue());
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }
}
