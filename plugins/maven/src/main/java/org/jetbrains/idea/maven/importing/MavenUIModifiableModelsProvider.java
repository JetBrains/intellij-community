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

import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectLibrariesConfigurable;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import org.jetbrains.idea.maven.utils.MavenUtil;

public class MavenUIModifiableModelsProvider extends MavenBaseModifiableModelsProvider {
  private final ModifiableModuleModel myModel;
  private final ModulesConfigurator myModulesConfigurator;
  private final ModifiableArtifactModel myModifiableArtifactModel;
  private final LibrariesModifiableModel myLibrariesModel;

  public MavenUIModifiableModelsProvider(Project project,
                                         ModifiableModuleModel model,
                                         ModulesConfigurator modulesConfigurator,
                                         ModifiableArtifactModel modifiableArtifactModel) {
    super(project);
    myModel = model;
    myModulesConfigurator = modulesConfigurator;
    myModifiableArtifactModel = modifiableArtifactModel;

    ProjectLibrariesConfigurable configurable = ProjectLibrariesConfigurable.getInstance(project);
    myLibrariesModel = (LibrariesModifiableModel)configurable.getModelProvider(true).getModifiableModel();
  }

  @Override
  protected ModifiableArtifactModel doGetArtifactModel() {
    return myModifiableArtifactModel;
  }

  @Override
  protected ModifiableModuleModel doGetModuleModel() {
    return myModel;
  }

  @Override
  protected ModifiableRootModel doGetRootModel(Module module) {
    return myModulesConfigurator.getOrCreateModuleEditor(module).getModifiableRootModel();
  }

  @Override
  protected ModifiableFacetModel doGetFacetModel(Module module) {
    return (ModifiableFacetModel)myModulesConfigurator.getFacetModel(module);
  }

  public Library[] getAllLibraries() {
    return myLibrariesModel.getLibraries();
  }

  public Library getLibraryByName(String name) {
    return myLibrariesModel.getLibraryByName(name);
  }

  public Library createLibrary(String name) {
    return myLibrariesModel.createLibrary(name);
  }

  public void removeLibrary(Library library) {
    myLibrariesModel.removeLibrary(library);
  }

  @Override
  protected Library.ModifiableModel doGetLibraryModel(Library library) {
    return myLibrariesModel.getLibraryModifiableModel(library);
  }

  public void commit() {
    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      public void run() {
        processExternalArtifactDependencies();
      }
    });
  }

  public void dispose() {
  }

  public ModalityState getModalityStateForQuestionDialogs() {
    return ModalityState.defaultModalityState();
  }
}
