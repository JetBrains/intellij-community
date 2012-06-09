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
import com.intellij.uiDesigner.propertyInspector.properties.IntroFontProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author yole
 */
public class FontEditor extends PropertyEditor<FontDescriptor> {
  private final TextFieldWithBrowseButton myTextField = new TextFieldWithBrowseButton();
  private FontDescriptor myValue;
  private Project myProject;
  private final String myPropertyName;

  public FontEditor(String propertyName) {
    myPropertyName = propertyName;
    myTextField.getTextField().setBorder(null);
    myTextField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showFontEditorDialog();
      }
    });
  }

  private void showFontEditorDialog() {
    FontEditorDialog dlg = new FontEditorDialog(myProject, myPropertyName);
    dlg.setValue(myValue);
    dlg.show();
    if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      myValue = dlg.getValue();
      myTextField.setText(IntroFontProperty.descriptorToString(myValue));
      fireValueCommitted(true, false);
    }
  }

  public FontDescriptor getValue() throws Exception {
    return myValue;
  }

  public JComponent getComponent(RadComponent component, FontDescriptor value, InplaceContext inplaceContext) {
    myProject = component.getProject();
    myValue = value != null ? value : new FontDescriptor(null, -1, -1);
    myTextField.setText(IntroFontProperty.descriptorToString(myValue));
    return myTextField;
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }
}
