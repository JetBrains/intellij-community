package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.IconDescriptor;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroIconProperty;
import com.intellij.util.containers.HashSet;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Set;

/**
 * @author yole
 */
public class IconEditor extends PropertyEditor {
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
          iconFile = ModuleUtil.findResourceFileInDependents(myModule, myValue.getIconPath(), PsiFile.class);
        }
        TreeFileChooser fileChooser = factory.createFileChooser(UIDesignerBundle.message("title.choose.icon.file"), iconFile,
                                                                null, new MyImageFileFilter(myModule));
        fileChooser.showDialog();
        PsiFile file = fileChooser.getSelectedFile();
        if (file != null) {
          PsiDirectory directory = file.getContainingDirectory();
          if (directory != null) {
            PsiPackage pkg = directory.getPackage();
            String packageName = pkg != null ? pkg.getQualifiedName() : "";
            IconDescriptor descriptor = new IconDescriptor(packageName.replace('.', '/') + '/' + file.getName());
            IntroIconProperty.loadIconFromFile(file, descriptor);
            myValue = descriptor;
            myTextField.setText(descriptor.getIconPath());
          }
        }
      }
    });
  }

  public Object getValue() throws Exception {
    return myValue;
  }

  public JComponent getComponent(RadComponent component, Object value, boolean inplace) {
    myValue = (IconDescriptor) value;
    myModule = component.getModule();
    if (myValue != null) {
      myTextField.setText(myValue.getIconPath());
    }
    return myTextField;
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTextField);
  }

  private static class MyImageFileFilter implements TreeFileChooser.PsiFileFilter {
    private Set<String> myExtensions;
    private GlobalSearchScope myModuleScope;

    public MyImageFileFilter(Module module) {
      myExtensions = new HashSet<String>(Arrays.asList(ImageIO.getReaderFormatNames()));
      myModuleScope = GlobalSearchScope.moduleWithDependenciesScope(module);
    }

    public boolean accept(PsiFile file) {
      final VirtualFile virtualFile = file.getVirtualFile();
      return virtualFile != null &&
             myExtensions.contains(virtualFile.getExtension()) &&
             myModuleScope.contains(virtualFile);
    }
  }
}
