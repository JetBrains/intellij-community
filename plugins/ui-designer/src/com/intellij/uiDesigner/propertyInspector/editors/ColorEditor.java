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
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.ColorDescriptor;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class ColorEditor extends PropertyEditor<ColorDescriptor> {
  private final String myPropertyName;
  private final TextFieldWithBrowseButton myTextField = new TextFieldWithBrowseButton();
  private ColorDescriptor myValue;

  public ColorEditor(String propertyName) {
    myPropertyName = propertyName;
    myTextField.getTextField().setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
    myTextField.getTextField().setEditable(false);
    myTextField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String title = UIDesignerBundle.message("color.chooser.title", myPropertyName);
        Color color = ColorChooser.chooseColor(myTextField, title , myValue.getColor());
        if (color != null) {
          myValue = new ColorDescriptor(color);
          updateTextField();
        }
      }
    });
  }

  public ColorDescriptor getValue() throws Exception {
    return myValue;
  }

  public JComponent getComponent(RadComponent component, ColorDescriptor value, InplaceContext inplaceContext) {
    myValue = value != null ? value : new ColorDescriptor(JBColor.BLACK);
    updateTextField();
    return myTextField;
  }

  private void updateTextField() {
    myTextField.setText(myValue == null ? "" : myValue.toString());
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }
}
