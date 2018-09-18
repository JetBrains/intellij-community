// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroFontProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
      @Override
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

  @Override
  public FontDescriptor getValue() throws Exception {
    return myValue;
  }

  @Override
  public JComponent getComponent(RadComponent component, FontDescriptor value, InplaceContext inplaceContext) {
    myProject = component.getProject();
    myValue = value != null ? value : new FontDescriptor(null, -1, -1);
    myTextField.setText(IntroFontProperty.descriptorToString(myValue));
    return myTextField;
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }
}
