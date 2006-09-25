/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author yole
 */
public class ListModelEditor extends PropertyEditor<String[]> {
  private TextFieldWithBrowseButton myTextField = new TextFieldWithBrowseButton();
  private RadComponent myLastComponent;
  private String[] myLastValue;
  private final String myPropertyName;

  public ListModelEditor(final String propertyName) {
    myPropertyName = propertyName;
    myTextField.getTextField().setBorder(null);
    myTextField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        openListEditorDialog();
      }
    });
  }

  private void openListEditorDialog() {
    ListEditorDialog dlg = new ListEditorDialog(myLastComponent.getProject(), myPropertyName);
    dlg.setValue(myLastValue);
    dlg.show();
    if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      myLastValue = dlg.getValue();
      myTextField.setText(listValueToString(myLastValue));
      fireValueCommitted(true, false);
    }
  }

  public String[] getValue() throws Exception {
    return myLastValue;
  }

  public JComponent getComponent(final RadComponent component, final String[] value, final boolean inplace) {
    myLastComponent = component;
    myLastValue = value;
    myTextField.setText(listValueToString(value));
    if (inplace) {
      openListEditorDialog();
    }
    return myTextField;
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }

  public static String listValueToString(final String[] value) {
    if (value == null) return "";
    return StringUtil.join(value, ", ");
  }
}
