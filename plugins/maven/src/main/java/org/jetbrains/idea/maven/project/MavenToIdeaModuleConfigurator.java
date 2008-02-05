package org.jetbrains.idea.maven.project;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.core.util.Strings;

import java.text.MessageFormat;
import java.util.Collection;
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
    new FoldersConfigurator(myMavenProject, myPrefs, myModel).config();
  }

  private void configDependencies() {
    for (Artifact artifact : ProjectUtil.extractDependencies(myMavenProject)) {
      MavenId id = new MavenId(artifact);

      if (isIgnored(id)) continue;

      boolean isExportable = ProjectUtil.isExportableDependency(artifact);

      String moduleName = findModuleFor(artifact);
      if (moduleName != null) {
        myModel.createModuleDependency(moduleName, isExportable);
      }
      else {
        String artifactPath = artifact.getFile().getPath();
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
