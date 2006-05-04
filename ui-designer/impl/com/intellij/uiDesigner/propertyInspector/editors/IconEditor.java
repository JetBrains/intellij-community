package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.uiDesigner.ImageFileFilter;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.lw.IconDescriptor;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroIconProperty;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class IconEditor extends PropertyEditor<IconDescriptor> {
  private TextFieldWithBrowseButton myTextField = new TextFieldWithBrowseButton();
  private IconDescriptor myValue;
  private Module myModule;

  public IconEditor() {
    myTextField.getTextField().setBorder(null);
    myTextField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myModule.getProject());
        PsiFile iconFile = null;
        if (myValue != null) {
          VirtualFile iconVFile = ModuleUtil.findResourceFileInDependents(myModule, myValue.getIconPath());
          if (iconVFile != null) {
            iconFile = PsiManager.getInstance(myModule.getProject()).findFile(iconVFile);
          }
        }
        TreeFileChooser fileChooser = factory.createFileChooser(UIDesignerBundle.message("title.choose.icon.file"), iconFile,
                                                                null, new ImageFileFilter(myModule));
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

  public IconDescriptor getValue() throws Exception {
    return myValue;
  }

  public JComponent getComponent(RadComponent component, IconDescriptor value, boolean inplace) {
    myValue = value;
    myModule = component.getModule();
    if (myValue != null) {
      myTextField.setText(myValue.getIconPath());
    }
    else {
      myTextField.setText("");
    }
    return myTextField;
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }

}
