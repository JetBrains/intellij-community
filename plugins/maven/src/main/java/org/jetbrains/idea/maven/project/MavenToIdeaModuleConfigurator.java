package org.jetbrains.idea.maven.project;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.core.util.Strings;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class MavenToIdeaModuleConfigurator {
  private ModifiableModuleModel myModuleModel;
  private MavenToIdeaMapping myMapping;
  private Collection<String> myProfiles;
  private MavenImporterSettings myPrefs;
  private Pattern myIgnorePatternCache;
  private Module myModule;
  private MavenProject myMavenProject;
  private RootModelAdapter myModel;

  public MavenToIdeaModuleConfigurator(ModifiableModuleModel moduleModel,
                                       MavenToIdeaMapping mapping,
                                       Collection<String> profiles,
                                       MavenImporterSettings prefs,
                                       Module module,
                                       MavenProject mavenProject) {
    myModuleModel = moduleModel;
    myMapping = mapping;
    myProfiles = profiles;
    myPrefs = prefs;
    myIgnorePatternCache = Pattern.compile(Strings.translateMasks(prefs.getIgnoredDependencies()));
    myModule = module;
    myMavenProject = mavenProject;
  }

  public void config() {
    myModel = new RootModelAdapter(myModule);
    myModel.init(myMavenProject.getFile().getParent());

    configModule();
    configFacets();
  }

  private void configModule() {
    configFolders();
    configDependencies();
    configLanguageLevel();

    myModel.commit();
  }

  private void configFacets() {
    for (FacetImporter importer : Extensions.getExtensions(FacetImporter.EXTENSION_POINT_NAME)) {
      if (importer.isApplicable(myMavenProject, myProfiles)) {
        importer.process(myModule, myMavenProject, myProfiles, myMapping, myModuleModel);
      }
    }
  }

  private void configFolders() {
    configSourceFolders();
    configFoldersUnderTargetDir();
    configBuildHelperPluginSources();
    configAntRunPluginSources();
    configOutputFolders();
  }

  private void configSourceFolders() {
    for (Object o : myMavenProject.getCompileSourceRoots()) {
      myModel.addSourceDir((String)o, false);
    }
    for (Object o : myMavenProject.getTestCompileSourceRoots()) {
      myModel.addSourceDir((String)o, true);
    }

    for (Object o : myMavenProject.getResources()) {
      myModel.addSourceDir(((Resource)o).getDirectory(), false);
    }
    for (Object o : myMavenProject.getTestResources()) {
      myModel.addSourceDir(((Resource)o).getDirectory(), true);
    }
  }

  private void configFoldersUnderTargetDir() {
    String path = myMavenProject.getBuild().getDirectory();
    VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(path);
    if (dir == null) return;

    for (VirtualFile f : dir.getChildren()) {
      if (!f.isDirectory()) continue;
      if (FileUtil.pathsEqual(f.getName(), "generated-sources")) {
        addAllSubDirsAsSources(f);
      }
      else {
       myModel.excludeRoot(f.getPath());
      }
    }
  }

  private void addAllSubDirsAsSources(VirtualFile dir) {
    for (VirtualFile f : dir.getChildren()) {
      if (!f.isDirectory()) continue;
      myModel.addSourceDir(f.getPath(), false);
    }
  }

  private void configBuildHelperPluginSources() {
    Plugin plugin = ProjectUtil.findPlugin(myMavenProject, myProfiles, "org.codehaus.mojo", "build-helper-maven-plugin");
    if (plugin == null) return;

    for (PluginExecution e : (List<PluginExecution>)plugin.getExecutions()) {
      for (String goal : (List<String>)e.getGoals()) {
        Xpp3Dom config = (Xpp3Dom)e.getConfiguration();
        if (config == null) continue;

        if (goal.equals("add-source")) addBuildHelperPluginSource(config, false);
        if (goal.equals("add-test-source")) addBuildHelperPluginSource(config, true);
      }
    }
  }

  private void addBuildHelperPluginSource(Xpp3Dom config, boolean isTestSources) {
    Xpp3Dom sources = config.getChild("sources");
    if (sources == null) return;

    for (Xpp3Dom source : sources.getChildren("source")) {
      myModel.addSourceDir(source.getValue(), isTestSources);
    }
  }

  private void configAntRunPluginSources() {
    Plugin plugin = ProjectUtil.findPlugin(myMavenProject, myProfiles, "org.apache.maven.plugins", "maven-antrun-plugin");
    if (plugin == null) return;

    for (PluginExecution e : (List<PluginExecution>)plugin.getExecutions()) {
      Xpp3Dom config = (Xpp3Dom)e.getConfiguration();
      if (config == null) continue;

      Xpp3Dom src = config.getChild("sourceRoot");
      Xpp3Dom test = config.getChild("testSourceRoot");

      if (src != null) myModel.addSourceDir(src.getValue(), false);
      if (test != null) myModel.addSourceDir(test.getValue(), true);
    }
  }

  private void configOutputFolders() {
    Build build = myMavenProject.getBuild();

    if (myPrefs.isUseMavenOutput()) {
      myModel.useModuleOutput(build.getOutputDirectory(), build.getTestOutputDirectory());
    }
    else {
      myModel.useProjectOutput();
      myModel.excludeRoot(build.getOutputDirectory());
      myModel.excludeRoot(build.getTestOutputDirectory());
    }
  }

  private void configDependencies() {
    for (Artifact artifact : ProjectUtil.extractDependencies(myMavenProject)) {
      MavenId id = new MavenId(artifact);

      if (isIgnored(id)) continue;

      String moduleName = findModuleFor(artifact);
      if (moduleName != null) {
        myModel.createModuleDependency(moduleName);
      }
      else {
        String artifactPath = artifact.getFile().getPath();
        boolean isExportable = ProjectUtil.isExportableDependency(artifact);
        myModel.createModuleLibrary(myMapping.getLibraryName(id),
                                    getUrl(artifactPath, null),
                                    getUrl(artifactPath, Constants.SOURCES_CLASSIFIER),
                                    getUrl(artifactPath, Constants.JAVADOC_CLASSIFIER),
                                    isExportable);
      }
    }
  }

  private boolean isIgnored(MavenId id) {
    return myIgnorePatternCache.matcher(id.toString()).matches();
  }

  private String findModuleFor(Artifact artifact) {
    // we should find module by version range version, since it might be X-SNAPSHOT or SNAPSHOT
    // which is resolved in X-timestamp-build. But mapping contains base artefact versions.
    String version = artifact.getVersionRange().toString();
    MavenId versionId = new MavenId(artifact.getGroupId(), artifact.getArtifactId(), version);
    return myMapping.getModuleName(versionId);
  }

  private String getUrl(String artifactPath, String classifier) {
    String path = artifactPath;

    if (classifier != null) {
      path = MessageFormat.format("{0}-{1}.jar", path.substring(0, path.lastIndexOf(".")), classifier);
    }

    String normalizedPath = FileUtil.toSystemIndependentName(path);
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, normalizedPath) + JarFileSystem.JAR_SEPARATOR;
  }

  private void configLanguageLevel() {
    String mavenLevel = extractLanguageLevel();
    LanguageLevel ideaLevel = translateLanguageLevel(mavenLevel);

    myModel.setLanguageLevel(ideaLevel);
  }

  @Nullable
  private String extractLanguageLevel() {
    return ProjectUtil.findPluginConfiguration(myMavenProject,
                                               myProfiles,
                                               "org.apache.maven.plugins",
                                               "maven-compiler-plugin",
                                               "source");
  }

  @Nullable
  private LanguageLevel translateLanguageLevel(@Nullable String level) {
    if ("1.3".equals(level)) return LanguageLevel.JDK_1_3;
    if ("1.4".equals(level)) return LanguageLevel.JDK_1_4;
    if ("1.5".equals(level)) return LanguageLevel.JDK_1_5;
    if ("1.6".equals(level)) return LanguageLevel.JDK_1_5;

    return null;
  }
}
