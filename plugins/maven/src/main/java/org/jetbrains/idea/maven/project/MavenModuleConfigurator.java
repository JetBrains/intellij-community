package org.jetbrains.idea.maven.project;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.Strings;
import org.jetbrains.idea.maven.facets.FacetImporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class MavenModuleConfigurator {
  private Module myModule;
  private ModifiableModuleModel myModuleModel;
  private MavenProjectsTree myMavenTree;
  private MavenProjectModel myMavenProject;
  private Map<MavenProjectModel, String> myMavenProjectToModuleName;
  private MavenImportSettings mySettings;
  private Pattern myIgnorePatternCache;
  private RootModelAdapter myRootModelAdapter;

  public MavenModuleConfigurator(Module module,
                                 ModifiableModuleModel moduleModel,
                                 MavenProjectsTree mavenTree,
                                 MavenProjectModel mavenProject,
                                 Map<MavenProjectModel, String> mavenProjectToModuleName,
                                 MavenImportSettings settings) {
    myModule = module;
    myModuleModel = moduleModel;
    myMavenTree = mavenTree;
    myMavenProject = mavenProject;
    myMavenProjectToModuleName = mavenProjectToModuleName;
    mySettings = settings;
    myIgnorePatternCache = Pattern.compile(Strings.translateMasks(settings.getIgnoredDependencies()));
  }

  public ModifiableRootModel getRootModel() {
    return myRootModelAdapter.getRootModel();
  }

  public void config(boolean isNewlyCreatedModule) {
    myRootModelAdapter = new RootModelAdapter(myModule);
    myRootModelAdapter.init(myMavenProject, isNewlyCreatedModule);

    configFolders();
    configDependencies();
    configLanguageLevel();
  }

  public void preConfigFacets() {
    for (FacetImporter importer : getSuitableFacetImporters()) {
      importer.preProcess(myModule, myMavenProject);
    }
  }

  public void configFacets(List<PostProjectConfigurationTask> postTasks) {
    for (FacetImporter importer : getSuitableFacetImporters()) {
      importer.process(myModuleModel,
                       myModule,
                       myRootModelAdapter,
                       myMavenTree,
                       myMavenProject,
                       myMavenProjectToModuleName,
                       postTasks);
    }
  }

  private List<FacetImporter> getSuitableFacetImporters() {
    List<FacetImporter> result = new ArrayList<FacetImporter>();
    for (FacetImporter importer : Extensions.getExtensions(FacetImporter.EXTENSION_POINT_NAME)) {
      if (importer.isApplicable(myMavenProject)) {
        result.add(importer);
      }
    }
    return result;
  }

  private void configFolders() {
    new MavenFoldersConfigurator(myMavenProject, mySettings, myRootModelAdapter).config();
  }

  private void configDependencies() {
    for (Artifact artifact : myMavenProject.getDependencies()) {

      if (isIgnored(new MavenId(artifact))) continue;

      boolean isExportable = myMavenProject.isExportableDependency(artifact);
      MavenProjectModel p = myMavenTree.findProject(artifact);
      if (p != null) {
        myRootModelAdapter.addModuleDependency(myMavenProjectToModuleName.get(p),
                                               isExportable);
      }
      else {
        myRootModelAdapter.addLibraryDependency(artifact, isExportable);
      }
    }
  }

  private boolean isIgnored(MavenId id) {
    return myIgnorePatternCache.matcher(id.toString()).matches();
  }

  private void configLanguageLevel() {
    String mavenLevel = extractLanguageLevel();
    LanguageLevel ideaLevel = translateLanguageLevel(mavenLevel);

    myRootModelAdapter.setLanguageLevel(ideaLevel);
  }

  @Nullable
  private String extractLanguageLevel() {
    return myMavenProject.findPluginConfigurationValue("org.apache.maven.plugins",
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
