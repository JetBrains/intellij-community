package com.intellij.projectImport;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class ProjectImportWizardStep extends ModuleWizardStep {
  protected static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");

  private final boolean isUpdating;

  public ProjectImportWizardStep(final boolean updating) {
    isUpdating = updating;
  }

  public Icon getIcon() {
    return isUpdating ? ICON : NEW_PROJECT_ICON;
  }
}
