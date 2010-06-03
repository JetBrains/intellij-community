package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.Module;
import org.jdom.Element;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.util.List;
import java.util.Map;

public abstract class GroovyImporter extends MavenImporter {
  public GroovyImporter(String pluginGroupID, String pluginArtifactID) {
    super(pluginGroupID, pluginArtifactID);
  }

  @Override
  public boolean isSupportedDependency(MavenArtifact artifact) {
    return false;
  }

  @Override
  public void preProcess(Module module, MavenProject mavenProject, MavenProjectChanges changes, MavenModifiableModelsProvider modifiableModelsProvider) {
  }

  @Override
  public void process(MavenModifiableModelsProvider modifiableModelsProvider, Module module, MavenRootModelAdapter rootModel,
                      MavenProjectsTree mavenModel, MavenProject mavenProject, MavenProjectChanges changes, Map<MavenProject, String> mavenProjectToModuleName,
                      List<MavenProjectsProcessorTask> postTasks) {
  }

  @Override
  public void collectSourceFolders(MavenProject mavenProject, List<String> result) {
    collectSourceOrTestFolders(mavenProject, "compile", "src/main/groovy", result);
  }

  @Override
  public void collectTestFolders(MavenProject mavenProject, List<String> result) {
    collectSourceOrTestFolders(mavenProject, "testCompile", "src/test/groovy", result);
  }

  private void collectSourceOrTestFolders(MavenProject mavenProject, String goal, String defaultDir, List<String> result) {
    Element sourcesElement = getGoalConfig(mavenProject, goal);
    List<String> dirs = MavenJDOMUtil.findChildrenValuesByPath(sourcesElement, "sources", "fileset.directory");
    if (dirs.isEmpty()) {
      result.add(mavenProject.getDirectory() + "/" + defaultDir);
      return;
    }
    result.addAll(dirs);
  }

  @Override
  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) {
    String stubsDir = findGoalConfigValue(mavenProject, "generateStubs", "outputDirectory");
    String testStubsDir = findGoalConfigValue(mavenProject, "generateTestStubs", "outputDirectory");

    // exclude common parent of /groovy-stubs/main and /groovy-stubs/test
    String defaultStubsDir = mavenProject.getGeneratedSourcesDirectory() + "/groovy-stubs";

    result.add(stubsDir == null ? defaultStubsDir : stubsDir);
    result.add(testStubsDir == null ? defaultStubsDir : testStubsDir);
  }
}
