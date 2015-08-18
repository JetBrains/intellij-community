/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.*;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class MavenDefaultModifiableModelsProvider extends MavenBaseModifiableModelsProvider {
  private final LibraryTable.ModifiableModel myLibrariesModel;

  public MavenDefaultModifiableModelsProvider(Project project) {
    super(project);
    myLibrariesModel = ProjectLibraryTable.getInstance(myProject).getModifiableModel();
  }

  @Override
  protected ModifiableArtifactModel doGetArtifactModel() {
    return new ReadAction<ModifiableArtifactModel>() {
      protected void run(@NotNull final Result<ModifiableArtifactModel> result) {
        result.setResult(ArtifactManager.getInstance(myProject).createModifiableModel());
      }
    }.execute().getResultObject();
  }

  @Override
  protected ModifiableModuleModel doGetModuleModel() {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      return ModuleManager.getInstance(myProject).getModifiableModel();
    }
    finally {
      accessToken.finish();
    }
  }

  @Override
  protected ModifiableRootModel doGetRootModel(@NotNull final Module module) {
    return new ReadAction<ModifiableRootModel>() {
      protected void run(@NotNull Result<ModifiableRootModel> result) throws Throwable {
        result.setResult(ModuleRootManager.getInstance(module).getModifiableModel());
      }
    }.execute().getResultObject();
  }

  @Override
  protected ModifiableFacetModel doGetFacetModel(Module module) {
    return FacetManager.getInstance(module).createModifiableModel();
  }

  @Override
  public LibraryTable.ModifiableModel getProjectLibrariesModel() {
    return myLibrariesModel;
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
    return library.getModifiableModel();
  }

  public void commit() {
    ((ProjectRootManagerEx)ProjectRootManager.getInstance(myProject)).mergeRootsChangesDuring(new Runnable() {
      public void run() {
        processExternalArtifactDependencies();
        for (Library.ModifiableModel each : myLibraryModels.values()) {
          each.commit();
        }
        myLibrariesModel.commit();
        Collection<ModifiableRootModel> rootModels = myRootModels.values();

        ModifiableRootModel[] rootModels1 = rootModels.toArray(new ModifiableRootModel[rootModels.size()]);
        for (ModifiableRootModel model : rootModels1) {
          assert !model.isDisposed() : "Already disposed: " + model;
        }

        if (myModuleModel != null) {
          ModifiableModelCommitter.multiCommit(rootModels1, myModuleModel);
        }

        for (ModifiableFacetModel each : myFacetModels.values()) {
          each.commit();
        }
        if (myArtifactModel != null) {
          myArtifactModel.commit();
        }
      }
    });
  }

  public void dispose() {
    for (ModifiableRootModel each : myRootModels.values()) {
      each.dispose();
    }
    myModuleModel.dispose();
    if (myArtifactModel != null) {
      myArtifactModel.dispose();
    }
  }

  public ModalityState getModalityStateForQuestionDialogs() {
    return ModalityState.NON_MODAL;
  }
}
