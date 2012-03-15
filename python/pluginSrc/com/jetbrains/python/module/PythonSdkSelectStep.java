package com.jetbrains.python.module;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author oleg
 */
public class PythonSdkSelectStep extends ModuleWizardStep {
  protected final PythonSdkChooserPanel myPanel;
  protected final PythonModuleBuilder mySettingsHolder;

  private final String myHelp;

  public PythonSdkSelectStep(@NotNull final PythonModuleBuilder settingsHolder,
                           @Nullable final String helpId,
                           @Nullable final Project project) {
    super();
    mySettingsHolder = settingsHolder;
    myPanel = new PythonSdkChooserPanel(project);
    myHelp = helpId;
  }

  public String getHelpId() {
    return myHelp;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  public JComponent getComponent() {
    return myPanel;
  }


  public void updateDataModel() {
    final Sdk sdk = getSdk();
    mySettingsHolder.setSdk(sdk);
  }

  @Nullable
  private Sdk getSdk() {
    return myPanel.getChosenJdk();
  }

  public Icon getIcon() {
    return ICON;
  }

  public boolean validate() {
    return true;
  }
}
