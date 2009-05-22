/*
 * User: anna
 * Date: 12-Jul-2007
 */
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardStepFactory;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.projectImport.SelectImportedProjectsStep;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.File;

public class MavenProjectImportProvider extends ProjectImportProvider {
  public MavenProjectImportProvider(final MavenProjectBuilder builder) {
    super(builder);
  }

  public ModuleWizardStep[] createSteps(WizardContext wizardContext) {
    final ProjectWizardStepFactory stepFactory = ProjectWizardStepFactory.getInstance();
    return new ModuleWizardStep[]{new MavenProjectImportStep(wizardContext), new SelectProfilesStep(wizardContext),
      new SelectImportedProjectsStep<MavenProject>(wizardContext) {
        protected String getElementText(final MavenProject project) {
          final StringBuilder stringBuilder = new StringBuilder();
          stringBuilder.append(project.getMavenId());
          final String relPath =
            VfsUtil.getRelativePath(project.getDirectoryFile(), ((MavenProjectBuilder)getBuilder()).getRootDirectory(), File.separatorChar);
          if (relPath.length() != 0) {
            stringBuilder.append(" [").append(relPath).append("]");
          }
          return stringBuilder.toString();
        }

        public void updateDataModel() {
          super.updateDataModel();
          getWizardContext().setProjectName(((MavenProjectBuilder)getBuilder()).getSuggestedProjectName());
        }

        public String getHelpId() {
          return "reference.dialogs.new.project.import.maven.page3";
        }
      }, stepFactory.createProjectJdkStep(wizardContext), stepFactory.createNameAndLocationStep(wizardContext)};
  }
}