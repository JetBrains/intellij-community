/*
 * User: anna
 * Date: 13-Jul-2007
 */
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessorBase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MavenProjectOpenProcessor extends ProjectOpenProcessorBase {
  public MavenProjectOpenProcessor(MavenProjectBuilder builder) {
    super(builder);
  }

  public MavenProjectBuilder getBuilder() {
    return (MavenProjectBuilder)super.getBuilder();
  }

  @Nullable
  public String[] getSupportedExtensions() {
    return new String[]{MavenConstants.POM_XML};
  }

  public boolean doQuickImport(VirtualFile file, WizardContext wizardContext) {
    getBuilder().setFiles(Arrays.asList(file));

    if (!getBuilder().setSelectedProfiles(new ArrayList<String>())) return false;

    List<MavenProject> projects = getBuilder().getList();
    if (projects.size() != 1) return false;

    getBuilder().setList(projects);
    wizardContext.setProjectName(getBuilder().getSuggestedProjectName());

    return true;
  }
}
