/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;
import java.util.Map;

public class MavenModuleImporter {
  private final Module myModule;
  private final MavenProjectsTree myMavenTree;
  private final MavenProject myMavenProject;
  private final MavenProjectChanges myMavenProjectChanges;
  private final Map<MavenProject, String> myMavenProjectToModuleName;
  private final MavenImportingSettings mySettings;
  private final MavenModifiableModelsProvider myModifiableModelsProvider;
  private MavenRootModelAdapter myRootModelAdapter;

  public MavenModuleImporter(Module module,
                             MavenProjectsTree mavenTree,
                             Pair<MavenProject, MavenProjectChanges> mavenProjectWithChanges,
                             Map<MavenProject, String> mavenProjectToModuleName,
                             MavenImportingSettings settings,
                             MavenModifiableModelsProvider modifiableModelsProvider) {
    myModule = module;
    myMavenTree = mavenTree;
    myMavenProject = mavenProjectWithChanges.first;
    myMavenProjectChanges = mavenProjectWithChanges.second;
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
    for (final MavenImporter importer : getSuitableImporters()) {
      // facets use FacetConfiguration and like that do not have modifiable models,
      // therefore we have to take write lock
      MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), new Runnable() {
        public void run() {
          importer.preProcess(myModule, myMavenProject, myMavenProjectChanges, myModifiableModelsProvider);
        }
      });
    }
  }

  public void configFacets(final List<MavenProjectsProcessorTask> postTasks) {
    for (final MavenImporter importer : getSuitableImporters()) {
      // facets use FacetConfiguration and like that do not have modifiable models,
      // therefore we have to take write lock
      MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), new Runnable() {
        public void run() {
          importer.process(myModifiableModelsProvider,
                           myModule,
                           myRootModelAdapter,
                           myMavenTree,
                           myMavenProject,
                           myMavenProjectChanges,
                           myMavenProjectToModuleName,
                           postTasks);
        }
      });
    }
  }

  private List<MavenImporter> getSuitableImporters() {
    return myMavenProject.getSuitableImporters();
  }

  private void configFolders() {
    new MavenFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).config();
  }

  private void configDependencies() {
    for (MavenArtifact artifact : myMavenProject.getDependencies()) {
      DependencyScope scope = selectScope(artifact.getScope());
      MavenProject depProject = myMavenTree.findProject(artifact.getMavenId());
      if (depProject != null) {
        myRootModelAdapter.addModuleDependency(myMavenProjectToModuleName.get(depProject), scope);
      }
      else if (myMavenProject.isSupportedDependency(artifact)) {
        myRootModelAdapter.addLibraryDependency(artifact, scope, myModifiableModelsProvider, myMavenProject);
      }
    }
  }

  private DependencyScope selectScope(String mavenScope) {
    if (MavenConstants.SCOPE_RUNTIME.equals(mavenScope)) return DependencyScope.RUNTIME;
    if (MavenConstants.SCOPE_TEST.equals(mavenScope)) return DependencyScope.TEST;
    if (MavenConstants.SCOPE_PROVIDEED.equals(mavenScope)) return DependencyScope.PROVIDED;
    return DependencyScope.COMPILE;
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
