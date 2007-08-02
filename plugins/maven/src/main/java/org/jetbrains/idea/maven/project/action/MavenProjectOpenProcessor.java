/*
 * User: anna
 * Date: 13-Jul-2007
 */
package org.jetbrains.idea.maven.project.action;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  public String[] getSupportedExtensions() {
    return new String[]{MavenEnv.POM_FILE};
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
    catch (ConfigurationException e) {
      return false;
    }

    if(projects.size()!=1){
      return false;
    }

    wizardContext.setProjectName(getBuilder().getSuggestedProjectName());
    return true;
  }
}