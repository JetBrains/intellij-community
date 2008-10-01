package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class SelectProfilesStep extends ProjectImportWizardStep {
  private JPanel panel;
  private ElementsChooser<String> profileChooser;

  public SelectProfilesStep(final WizardContext context) {
    super(context);
  }

  public boolean isStepVisible() {
    if (!super.isStepVisible()) {
      return false;
    }
    final MavenImportBuilder importBuilder = getBuilder();
    if (importBuilder != null) {
      return !importBuilder.getProfiles().isEmpty();
    }
    return false;
  }

  protected MavenImportBuilder getBuilder() {
    return (MavenImportBuilder)super.getBuilder();
  }

  public void createUIComponents (){
    profileChooser = new ElementsChooser<String>(true);
  }

  public JComponent getComponent() {
    return panel;
  }

  public void updateStep() {
    profileChooser.setElements(getBuilder().getProfiles(), false);
    profileChooser.markElements(getBuilder().getSelectedProfiles());
  }

  public boolean validate() throws ConfigurationException {
    return getBuilder().setSelectedProfiles(profileChooser.getMarkedElements());
  }

  public void updateDataModel() {
  }

  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.import.maven.page2";
  }
}
