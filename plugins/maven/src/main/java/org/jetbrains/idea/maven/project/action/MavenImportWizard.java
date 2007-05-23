package org.jetbrains.idea.maven.project.action;

import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import com.intellij.projectImport.ProjectImportWizard;
import org.jetbrains.idea.maven.project.MavenImportProcessor;
import org.jetbrains.idea.maven.project.MavenImporterPreferences;
import org.jetbrains.idea.maven.project.MavenImporterPreferencesComponent;
import org.jetbrains.idea.maven.project.ProjectBundle;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenImportWizard extends ProjectImportWizard {

  private MavenImportProcessor myImportProcessor;

  private MavenImporterPreferences preferencesForEditing;

  public String getName() {
    return ProjectBundle.message("maven.name");
  }

  public AddModuleWizard.ExtraStepsCreator getStepsFactory(final Project currentProject, final boolean updateCurrent) {

    if ((currentProject != null && updateCurrent)) {
      preferencesForEditing = MavenImporterPreferencesComponent.getInstance(currentProject).getState().safeClone();
    }
    else {
      preferencesForEditing = new MavenImporterPreferences();
    }

    myImportProcessor = new MavenImportProcessor(updateCurrent ? currentProject : null, preferencesForEditing);

    return new AddModuleWizard.ExtraStepsCreator() {
      public Collection<ModuleWizardStep> createSteps(final WizardContext wizardContext) {
        final Collection<ModuleWizardStep> list = new ArrayList<ModuleWizardStep>();
        list.add(new MavenImportRootStep(wizardContext, myImportProcessor, preferencesForEditing));
        list.add(new SelectMavenProjectsStep(myImportProcessor));
        return list;
      }
    };
  }

  public void commitImport(final Project project) {
    myImportProcessor.commitImport(project);
    MavenImporterPreferencesComponent.getInstance(project).loadState(preferencesForEditing);
  }
}
