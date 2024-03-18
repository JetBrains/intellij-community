// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.ide.util.TreeFileChooser;
import com.intellij.ide.util.TreeFileChooserFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ResourceFileUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.ImageFileFilter;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.IconDescriptor;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroIconProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class IconEditor extends PropertyEditor<IconDescriptor> {
  private final TextFieldWithBrowseButton myTextField = new TextFieldWithBrowseButton();
  private IconDescriptor myValue;
  private RadComponent myComponent;

  public IconEditor() {
    myTextField.getTextField().setBorder(null);
    myTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final TreeFileChooserFactory factory = TreeFileChooserFactory.getInstance(getModule().getProject());
        PsiFile iconFile = null;
        if (myValue != null) {
          VirtualFile iconVFile = ResourceFileUtil.findResourceFileInScope(myValue.getIconPath(), getModule().getProject(),
                                                                           getModule()
                                                                             .getModuleWithDependenciesAndLibrariesScope(true));
          if (iconVFile != null) {
            iconFile = PsiManager.getInstance(getModule().getProject()).findFile(iconVFile);
          }
        }
        TreeFileChooser fileChooser = factory.createFileChooser(UIDesignerBundle.message("title.choose.icon.file"), iconFile,
                                                                null, new ImageFileFilter(getModule()), false, true);
        fileChooser.showDialog();
        PsiFile file = fileChooser.getSelectedFile();
        if (file != null) {
          String resourceName = FormEditingUtil.buildResourceName(file);
          if (resourceName != null) {
            IconDescriptor descriptor = new IconDescriptor(resourceName);
            IntroIconProperty.loadIconFromFile(file.getVirtualFile(), descriptor);
            myValue = descriptor;
            myTextField.setText(descriptor.getIconPath());
          }
        }
      }
    });
  }

  private Module getModule() {
    return myComponent.getModule();
  }

  @Override
  public IconDescriptor getValue() throws Exception {
    if (myTextField.getText().isEmpty()) {
      return null;
    }
    final IconDescriptor descriptor = new IconDescriptor(myTextField.getText());
    IntroIconProperty.ensureIconLoaded(getModule(), descriptor);
    return descriptor;
  }

  @Override
  public JComponent getComponent(RadComponent component, IconDescriptor value, InplaceContext inplaceContext) {
    myValue = value;
    myComponent = component;
    if (myValue != null) {
      myTextField.setText(myValue.getIconPath());
    }
    else {
      myTextField.setText("");
    }
    return myTextField;
  }

  @Override
  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }
}
