package org.jetbrains.idea.maven.project.action;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.idea.maven.project.MavenImportProcessorContext;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class SelectProfilesStep extends ProjectImportWizardStep {
  private JPanel panel;
  private ElementsChooser<String> profileChooser;
  private final MavenImportProcessorContext myContext;

  public SelectProfilesStep(final MavenImportProcessorContext context) {
    super(context.getUpdatedProject()!=null);
    myContext = context;
  }

  public void createUIComponents (){
    profileChooser = new ElementsChooser<String>(true);
  }

  public JComponent getComponent() {
    return panel;
  }

  public void updateStep() {
    profileChooser.setElements(myContext.getProfiles(), false);
  }

  public boolean validate() {
    return myContext.setProfiles(profileChooser.getMarkedElements());
  }

  public void updateDataModel() {
  }
}
