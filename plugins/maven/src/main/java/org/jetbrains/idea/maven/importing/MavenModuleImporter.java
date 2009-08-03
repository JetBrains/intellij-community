package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facets.FacetImporter;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;
import java.util.Map;

public class MavenModuleImporter {
  private final Module myModule;
  private final MavenProjectsTree myMavenTree;
  private final MavenProject myMavenProject;
  private final Map<MavenProject, String> myMavenProjectToModuleName;
  private final MavenImportingSettings mySettings;
  private final MavenModifiableModelsProvider myModifiableModelsProvider;
  private MavenRootModelAdapter myRootModelAdapter;

  public MavenModuleImporter(Module module,
                             MavenProjectsTree mavenTree,
                             MavenProject mavenProject,
                             Map<MavenProject, String> mavenProjectToModuleName,
                             MavenImportingSettings settings,
                             MavenModifiableModelsProvider modifiableModelsProvider) {
    myModule = module;
    myMavenTree = mavenTree;
    myMavenProject = mavenProject;
    myMavenProjectToModuleName = mavenProjectToModuleName;
    mySettings = settings;
    myModifiableModelsProvider = modifiableModelsProvider;
  }

  public ModifiableRootModel getRootModel() {
    return myRootModelAdapter.getRootModel();
  }

  public void config(boolean isNewlyCreatedModule) {
    myRootModelAdapter = new MavenRootModelAdapter(myMavenProject, myModule, myModifiableModelsProvider);
    myRootModelAdapter.init(isNewlyCreatedModule);

    configFolders();
    configDependencies();
    configLanguageLevel();
  }

  public void preConfigFacets() {
    for (final FacetImporter importer : getSuitableFacetImporters()) {
      // facets use FacetConfiguration and like that do not have modifiable models,
      // therefore we have to take write lock
      MavenUtil.invokeAndWait(myModule.getProject(), new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              importer.preProcess(myModule, myMavenProject, myModifiableModelsProvider);
            }
          });
        }
      });
    }
  }

  public void configFacets(final List<MavenProjectsProcessorTask> postTasks) {
    for (final FacetImporter importer : getSuitableFacetImporters()) {
      // facets use FacetConfiguration and like that do not have modifiable models,
      // therefore we have to take write lock
      MavenUtil.invokeAndWait(myModule.getProject(), new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              importer.process(myModifiableModelsProvider,
                               myModule,
                               myRootModelAdapter,
                               myMavenTree,
                               myMavenProject,
                               myMavenProjectToModuleName,
                               postTasks);
            }
          });
        }
      });
    }
  }

  private List<FacetImporter> getSuitableFacetImporters() {
    return myMavenProject.getSuitableFacetImporters();
  }

  private void configFolders() {
    new MavenFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).config();
  }

  private void configDependencies() {
    for (MavenArtifact artifact : myMavenProject.getDependencies()) {
      boolean isExportable = artifact.isExportable();
      MavenProject depProject = myMavenTree.findProject(artifact.getMavenId());
      if (depProject != null) {
        myRootModelAdapter.addModuleDependency(myMavenProjectToModuleName.get(depProject), isExportable);
      }
      else if (myMavenProject.isSupportedDependency(artifact)) {
        myRootModelAdapter.addLibraryDependency(artifact, isExportable, myModifiableModelsProvider);
      }
    }
  }

  private void configLanguageLevel() {
    LanguageLevel level = translateLanguageLevel(myMavenProject.getSourceLevel());
    myRootModelAdapter.setLanguageLevel(level);
  }

  @Nullable
  private LanguageLevel translateLanguageLevel(@Nullable String level) {
    if ("1.3".equals(level)) return LanguageLevel.JDK_1_3;
    if ("1.4".equals(level)) return LanguageLevel.JDK_1_4;
    if ("1.5".equals(level)) return LanguageLevel.JDK_1_5;
    if ("1.6".equals(level)) return LanguageLevel.JDK_1_6;

    return null;
  }
}
