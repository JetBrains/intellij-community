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
package com.jetbrains.python.documentation;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class PythonDocumentationEntryEditor extends DialogWrapper {
  private JPanel myPanel;
  private JTextField myNameField;
  private JTextField myURLPatternTextField;
  private JButton myInsertButton;
  private JBList myMacroList;

  public PythonDocumentationEntryEditor(Component parent) {
    super(parent, true);
    init();
    setTitle("Edit Documentation URL");
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
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public PythonDocumentationMap.Entry getEntry() {
    return new PythonDocumentationMap.Entry(myNameField.getText(), myURLPatternTextField.getText());
  }

  public void setEntry(PythonDocumentationMap.Entry entry) {
    myNameField.setText(entry.getPrefix());
    myURLPatternTextField.setText(entry.getUrlPattern());
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
