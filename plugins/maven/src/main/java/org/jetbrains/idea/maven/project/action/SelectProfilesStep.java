package org.jetbrains.idea.maven.project.action;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportWizardStep;

import javax.swing.*;
import java.util.List;

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
      final List<String> list = importBuilder.getProfiles();
      return list != null && !list.isEmpty();
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
  }

  public boolean validate() {
    return getBuilder().setProfiles(profileChooser.getMarkedElements());
  }

  public void updateDataModel() {
  }
}
