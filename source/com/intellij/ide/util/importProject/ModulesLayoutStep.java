package com.intellij.ide.util.importProject;

import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 18, 2007
 */
public class ModulesLayoutStep extends ModuleWizardStep {
  private final ProjectFromSourcesBuilder myBuilder;
  private final Icon myIcon;
  private final String myHelpId;
  private ModulesLayoutPanel myModulesLayoutPanel;

  public ModulesLayoutStep(ProjectFromSourcesBuilder builder, Icon icon, @NonNls String helpId) {
    myBuilder = builder;
    myIcon = icon;
    myHelpId = helpId;
  }

  public JComponent getComponent() {
    myModulesLayoutPanel = new ModulesLayoutPanel(myBuilder);
    return myModulesLayoutPanel;
  }

  public void updateStep() {
    myModulesLayoutPanel.rebuild();
  }

  public JComponent getPreferredFocusedComponent() {
    return myModulesLayoutPanel;
  }

  public void updateDataModel() {
  }

  @NonNls
  public String getHelpId() {
    return myHelpId;
  }

  public Icon getIcon() {
    return myIcon;
  }
}
