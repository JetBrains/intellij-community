/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
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
  private final TextFieldWithBrowseButton myTextField = new TextFieldWithBrowseButton();
  private RadComponent myLastComponent;
  private String[] myLastValue;
  private final String myPropertyName;

  public ListModelEditor(final String propertyName) {
    myPropertyName = propertyName;
    myTextField.getTextField().setBorder(null);
    myTextField.getTextField().setEditable(false);
    myTextField.addActionListener(new ActionListener() {
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

  public String[] getValue() throws Exception {
    return myLastValue;
  }

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

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }

  public static String listValueToString(final String[] value) {
    if (value == null) return "";
    return StringUtil.join(value, ", ");
  }
}
