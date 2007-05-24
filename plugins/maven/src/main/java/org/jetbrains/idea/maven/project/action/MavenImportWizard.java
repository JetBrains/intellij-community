package org.jetbrains.idea.maven.project.action;

import com.intellij.ide.util.projectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.projectImport.ProjectImportWizard;
import com.intellij.projectImport.SelectImportedProjectsStep;
import org.jetbrains.idea.maven.project.*;

import java.io.File;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenImportWizard extends ProjectImportWizard implements SelectImportedProjectsStep.Context<MavenProjectModel.Node> {

  private MavenImportProcessor myImportProcessor;

  private MavenImporterPreferences preferencesForEditing;
  private boolean openModulesConfigurator;

  public String getName() {
    return ProjectBundle.message("maven.name");
  }

  public AddModuleWizard.ModuleWizardStepFactory getStepsFactory(final Project currentProject, final boolean updateCurrent) {

    if ((currentProject != null && updateCurrent)) {
      preferencesForEditing = MavenImporterPreferencesComponent.getInstance(currentProject).getState().safeClone();
    }
    else {
      preferencesForEditing = new MavenImporterPreferences();
    }

    myImportProcessor = new MavenImportProcessor(updateCurrent ? currentProject : null, preferencesForEditing);

    return new AddModuleWizard.ModuleWizardStepFactory() {
      public ModuleWizardStep[] createSteps(final WizardContext wizardContext) {
        return new ModuleWizardStep[]{new MavenImportRootStep(wizardContext, myImportProcessor, preferencesForEditing),
          new SelectImportedProjectsStep<MavenProjectModel.Node>(MavenImportWizard.this, updateCurrent) {
            protected String getElementText(final MavenProjectModel.Node node) {
              final StringBuilder stringBuilder = new StringBuilder();
              stringBuilder.append(node.getArtifact().toString());
              final String relPath =
                VfsUtil.getRelativePath(node.getFile().getParent(), myImportProcessor.getRootDirectory(), File.separatorChar);
              if (relPath.length() != 0) {
                stringBuilder.append(" [").append(relPath).append("]");
              }
              return stringBuilder.toString();
            }
          }};
      }
    };
  }

  public void commitImport(final Project project) {
    myImportProcessor.commitImport(project);
    MavenImporterPreferencesComponent.getInstance(project).loadState(preferencesForEditing);
  }

  public List<MavenProjectModel.Node> getList() {
    return myImportProcessor.getSelectedProjects();
  }

  public void setList(List<MavenProjectModel.Node> nodes) {
    myImportProcessor.selectProjects(nodes);
    myImportProcessor.createMavenToIdeaMapping(false);
  }

  public boolean isOpenProjectSettingsAfter() {
    return openModulesConfigurator;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    openModulesConfigurator = on;
  }
}
