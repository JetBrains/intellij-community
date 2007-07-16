package com.intellij.projectImport;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class ProjectImportWizardStep extends ModuleWizardStep {
  protected static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");
  private final WizardContext myContext;

  public ProjectImportWizardStep(WizardContext context) {
    myContext = context;
  }

  public Icon getIcon() {
    return getBuilder() != null && getBuilder().isUpdate() ? ICON : NEW_PROJECT_ICON;
  }
  
  protected ProjectImportBuilder getBuilder() {
    return (ProjectImportBuilder)myContext.getProjectBuilder();
  }

  protected WizardContext getWizardContext() {
    return myContext;
  }

  protected void suggestProjectNameAndPath(final String alternativePath, final String path) {
    getWizardContext().setProjectFileDirectory(alternativePath != null && alternativePath.length() > 0 ? alternativePath : path);
    final String global = FileUtil.toSystemIndependentName(path);
    getWizardContext().setProjectName(global.substring(global.lastIndexOf("/") + 1));
  }
}
