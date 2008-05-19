package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.pom.java.LanguageLevel;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.ProjectId;
import org.jetbrains.idea.maven.core.util.ProjectUtil;
import org.jetbrains.idea.maven.core.util.Strings;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class ModuleConfigurator {
  private ModifiableModuleModel myModuleModel;
  private MavenToIdeaMapping myMapping;
  private Collection<String> myProfiles;
  private MavenImporterSettings mySettings;
  private Pattern myIgnorePatternCache;
  private Module myModule;
  private MavenProject myMavenProject;
  private RootModelAdapter myModel;

  public ModuleConfigurator(ModifiableModuleModel moduleModel,
                            MavenToIdeaMapping mapping,
                            Collection<String> profiles,
                            MavenImporterSettings settings,
                            Module module,
                            MavenProject mavenProject) {
    myModuleModel = moduleModel;
    myMapping = mapping;
    myProfiles = profiles;
    mySettings = settings;
    myIgnorePatternCache = Pattern.compile(Strings.translateMasks(settings.getIgnoredDependencies()));
    myModule = module;
    myMavenProject = mavenProject;
  }

  public void config(List<ModifiableRootModel> rootModels) {
    myModel = new RootModelAdapter(myModule);
    myModel.init(myMavenProject);

    configFolders();
    configDependencies();
    configLanguageLevel();

    rootModels.add(myModel.getRootModel());
  }

  public void configFacets() {
    for (FacetImporter importer : Extensions.getExtensions(FacetImporter.EXTENSION_POINT_NAME)) {
      if (importer.isApplicable(myMavenProject, myProfiles)) {
        importer.process(myModule, myMavenProject, myProfiles, myMapping, myModuleModel);
      }
    }
  }

  private void configFolders() {
    new FoldersConfigurator(myMavenProject, mySettings, myModel).config();
  }

  private void configDependencies() {
    for (Artifact artifact : ProjectUtil.extractDependencies(myMavenProject)) {
      MavenId id = new MavenId(artifact);

      if (isIgnored(id)) continue;

      boolean isExportable = ProjectUtil.isExportableDependency(artifact);

      String moduleName = myMapping.getModuleName(new ProjectId(artifact));
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
    return ProjectUtil.findPluginConfigurationValue(myMavenProject,
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
