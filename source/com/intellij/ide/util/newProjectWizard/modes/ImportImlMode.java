/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ExistingModuleLoader;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ImportImlMode implements CreationMode {
  private TextFieldWithBrowseButton myModulePathFieldPanel;

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return IdeBundle.message("radio.import.existing.module");
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    return IdeBundle.message("prompt.select.module.file.to.import", ApplicationNamesInfo.getInstance().getProductName());
  }


  public StepSequence getSteps(final WizardContext context, final ModulesProvider modulesProvider) {
    return null;
  }

  public boolean isAvailable(WizardContext context) {
    return context.getProject() != null;
  }

  public ProjectBuilder getModuleBuilder() {
    final ExistingModuleLoader moduleLoader = new ExistingModuleLoader();
    final String moduleFilePath = FileUtil.toSystemIndependentName(myModulePathFieldPanel.getText().trim());
    moduleLoader.setModuleFilePath(moduleFilePath);
    final int startIndex = moduleFilePath.lastIndexOf('/');
    final int endIndex = moduleFilePath.lastIndexOf(".");
    if (startIndex >= 0 && endIndex > startIndex + 1) {
      final String name = moduleFilePath.substring(startIndex + 1, endIndex);
      moduleLoader.setName(name);
    }
    return moduleLoader;
  }

  public void dispose() {
    myModulePathFieldPanel = null; //todo
  }

  public JComponent getAdditionalSettings() {
    JTextField tfModuleFilePath = new JTextField();
    final String productName = ApplicationNamesInfo.getInstance().getProductName();
    myModulePathFieldPanel = new TextFieldWithBrowseButton(tfModuleFilePath, new BrowseFilesListener(tfModuleFilePath, IdeBundle.message(
      "prompt.select.module.file.to.import", productName), null, new ModuleFileChooserDescriptor()));
    onChosen(false);
    return myModulePathFieldPanel;
  }

  public void onChosen(final boolean enabled) {
    UIUtil.setEnabled(myModulePathFieldPanel, enabled, true);
  }

  private static class ModuleFileChooserDescriptor extends FileChooserDescriptor {
    public ModuleFileChooserDescriptor() {
      super(true, false, false, false, false, false);
      setHideIgnored(false);
    }

    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      final boolean isVisible = super.isFileVisible(file, showHiddenFiles);
      if (!isVisible || file.isDirectory()) {
        return isVisible;
      }
      return StdFileTypes.IDEA_MODULE.equals(FileTypeManager.getInstance().getFileTypeByFile(file));
    }
  }
}