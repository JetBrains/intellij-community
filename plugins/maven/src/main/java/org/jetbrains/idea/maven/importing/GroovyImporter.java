package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.Module;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.*;

import java.util.List;
import java.util.Map;

public class GroovyImporter extends MavenImporter {
  private static final String ourPluginGroupID = "org.codehaus.groovy.maven";
  private static final String ourPluginArtifactID = "gmaven-plugin";

  public boolean isApplicable(MavenProject project) {
    return project.findPlugin(ourPluginGroupID, ourPluginArtifactID) != null;
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

  protected static Element findGoalConfigNode(MavenProject p, String goal, String path) {
    return p.findPluginGoalConfigurationElement(ourPluginGroupID, ourPluginArtifactID, goal, path);
  }


  private void collectSourceOrTestFolders(MavenProject mavenProject, String goal, String defaultDir, List<String> result) {
    Element sourcesElement = findGoalConfigNode(mavenProject, goal, "sources");
    if (sourcesElement == null) {
      result.add(mavenProject.getDirectory() + "/" + defaultDir);
      return;
    }

    for (Element each : (Iterable<? extends Element>)sourcesElement.getChildren("fileset")) {
      String dir = findChildElementValue(each, "directory", null);
      if (dir == null) continue;
      result.add(dir);
    }
  }

  private static String findChildElementValue(Element parent, String childName, String defaultValue) {
    if (parent == null) return defaultValue;
    Element child = parent.getChild(childName);
    return child == null ? defaultValue : child.getValue();
  }


  private static String findGoalConfigValue(MavenProject p, String goal, String path) {
    return p.findPluginGoalConfigurationValue(ourPluginGroupID, ourPluginArtifactID, goal, path);
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
