// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class ListModelEditor extends PropertyEditor<String[]> {
  private final TextFieldWithBrowseButton myTextField = new TextFieldWithBrowseButton();
  private RadComponent myLastComponent;
  private String[] myLastValue;
  private final String myPropertyName;

  public ListModelEditor(final String propertyName) {
    myPropertyName = propertyName;
    myTextField.getTextField().setBorder(null);
    myTextField.getTextField().setEditable(false);
    myTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        openListEditorDialog(myLastValue);
      }
    });
  }

  private void openListEditorDialog(String[] value) {
    ListEditorDialog dlg = new ListEditorDialog(myLastComponent.getProject(), myPropertyName);
    dlg.setValue(value);
    dlg.show();
    if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      myLastValue = dlg.getValue();
      myTextField.setText(listValueToString(myLastValue));
      fireValueCommitted(true, false);
    }
  }

  @Override
  public String[] getValue() throws Exception {
    return myLastValue;
  }

  @Override
  public JComponent getComponent(final RadComponent component, final String[] value, final InplaceContext inplaceContext) {
    myLastComponent = component;
    myLastValue = value;
    if (inplaceContext != null) {
      if (inplaceContext.isStartedByTyping()) {
        openListEditorDialog(new String[] { Character.toString(inplaceContext.getStartChar()) });
      }
      else {
        openListEditorDialog(value);
      }
      inplaceContext.setModalDialogDisplayed(true);
    }
    else {
      myTextField.setText(listValueToString(value));
    }
    return myTextField;
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }

  public static String listValueToString(final String[] value) {
    if (value == null) return "";
    return StringUtil.join(value, ", ");
  }
}
