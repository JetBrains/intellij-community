package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.idea.maven.project.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public abstract class MavenImporter {
  public static ExtensionPointName<MavenImporter> EXTENSION_POINT_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.importer");

  public static List<MavenImporter> getSuitableImporters(MavenProject p) {
    final List<MavenImporter> result = new ArrayList<MavenImporter>();
    for (MavenImporter importer : EXTENSION_POINT_NAME.getExtensions()) {
      if (importer.isApplicable(p)) {
        result.add(importer);
      }
    }
    return result;
  }

  public abstract boolean isApplicable(MavenProject mavenProject);

  public abstract boolean isSupportedDependency(MavenArtifact artifact);

  public abstract void preProcess(Module module,
                         MavenProject mavenProject,
                         MavenProjectChanges changes,
                         MavenModifiableModelsProvider modifiableModelsProvider);

  public abstract void process(MavenModifiableModelsProvider modifiableModelsProvider,
                      Module module,
                      MavenRootModelAdapter rootModel,
                      MavenProjectsTree mavenModel,
                      MavenProject mavenProject,
                      MavenProjectChanges changes,
                      Map<MavenProject, String> mavenProjectToModuleName,
                      List<MavenProjectsProcessorTask> postTasks);

  public void collectSourceFolders(MavenProject mavenProject, List<String> result) {
  }

  public void collectTestFolders(MavenProject mavenProject, List<String> result) {
  }

  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) {
  }
}
