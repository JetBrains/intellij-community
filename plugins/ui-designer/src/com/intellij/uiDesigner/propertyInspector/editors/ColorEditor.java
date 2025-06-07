// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ColorChooserService;
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

public final class ColorEditor extends PropertyEditor<ColorDescriptor> {
  private final String myPropertyName;
  private final TextFieldWithBrowseButton myTextField = new TextFieldWithBrowseButton();
  private ColorDescriptor myValue;

  public ColorEditor(String propertyName) {
    myPropertyName = propertyName;
    myTextField.getTextField().setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
    myTextField.getTextField().setEditable(false);
    myTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String title = UIDesignerBundle.message("color.chooser.title", myPropertyName);
        Color color = ColorChooserService.getInstance().showDialog(myTextField, title , myValue.getColor());
        if (color != null) {
          myValue = new ColorDescriptor(color);
          updateTextField();
        }
      }
    });
  }

  @Override
  public ColorDescriptor getValue() throws Exception {
    return myValue;
  }

  @Override
  public JComponent getComponent(RadComponent component, ColorDescriptor value, InplaceContext inplaceContext) {
    myValue = value != null ? value : new ColorDescriptor(JBColor.BLACK);
    updateTextField();
    return myTextField;
  }

  private void updateTextField() {
    myTextField.setText(myValue == null ? "" : myValue.toString());
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }
}
