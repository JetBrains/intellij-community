/*
 * User: anna
 * Date: 13-Jul-2007
 */
package org.jetbrains.idea.maven.project.action;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.projectImport.SelectImportedProjectsStep;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.project.MavenProjectModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MavenProjectOpenProcessor extends ProjectOpenProcessor {
  public MavenProjectOpenProcessor(final MavenImportBuilder builder) {
    super(builder);
  }

  public MavenImportBuilder getBuilder() {
    return (MavenImportBuilder)super.getBuilder();
  }


  public boolean canOpenProject(VirtualFile file) {
    return file.getName().equals(MavenEnv.POM_FILE);
  }

  public boolean doQuickImport(VirtualFile file, final WizardContext wizardContext) {
    getBuilder().setFiles(Arrays.asList(file));

    if(!getBuilder().setProfiles(new ArrayList<String>())){
      return false;
    }

    final List<MavenProjectModel.Node> projects = getBuilder().getList();
    try {
      getBuilder().setList(projects);
    }
    catch (SelectImportedProjectsStep.Context.ValidationException e) {
      return false;
    }

    if(projects.size()!=1){
      return false;
    }

    wizardContext.setProjectName(projects.get(0).getMavenProject().getArtifactId());
    return true;
  }
}