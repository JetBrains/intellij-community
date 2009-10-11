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

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.artifacts.ArtifactManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;

public class MavenDefaultModifiableModelsProvider extends MavenBaseModifiableModelsProvider {
  private final LibraryTable.ModifiableModel myLibrariesModel;
  private volatile long myCommitTime;

  public MavenDefaultModifiableModelsProvider(Project project) {
    super(project);
    myLibrariesModel = ProjectLibraryTable.getInstance(myProject).getModifiableModel();
  }

  @Override
  protected ModifiableArtifactModel doGetArtifactModel() {
    return new ReadAction<ModifiableArtifactModel>() {
      protected void run(final Result<ModifiableArtifactModel> result) {
        result.setResult(ArtifactManager.getInstance(myProject).createModifiableModel());
      }
    }.execute().getResultObject();
  }

  @Override
  protected ModifiableModuleModel doGetModuleModel() {
    return new ReadAction<ModifiableModuleModel>() {
      protected void run(Result<ModifiableModuleModel> result) throws Throwable {
        result.setResult(ModuleManager.getInstance(myProject).getModifiableModel());
      }
    }.execute().getResultObject();
  }

  @Override
  protected ModifiableRootModel doGetRootModel(final Module module) {
    return new ReadAction<ModifiableRootModel>() {
      protected void run(Result<ModifiableRootModel> result) throws Throwable {
        result.setResult(ModuleRootManager.getInstance(module).getModifiableModel());
      }
    }.execute().getResultObject();
  }

  @Override
  protected ModifiableFacetModel doGetFacetModel(Module module) {
    return FacetManager.getInstance(module).createModifiableModel();
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
    long before = System.currentTimeMillis();

    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      public void run() {
        processExternalArtifactDependencies();
        // all rootChanges will be merged and postponed until writeAction finishes.
        for (Library.ModifiableModel each : myLibraryModels.values()) {
          each.commit();
        }
        myLibrariesModel.commit();
        Collection<ModifiableRootModel> rootModels = myRootModels.values();

        ProjectRootManager.getInstance(myProject).multiCommit(myModuleModel,
                                                              rootModels.toArray(new ModifiableRootModel[rootModels.size()]));

        for (ModifiableFacetModel each : myFacetModels.values()) {
          each.commit();
        }
        if (myArtifactModel != null) {
          myArtifactModel.commit();
        }
      }
    });

    myCommitTime = System.currentTimeMillis() - before;
  }

  public void dispose() {
    MavenUtil.invokeAndWaitWriteAction(myProject, new Runnable() {
      public void run() {
        for (ModifiableRootModel each : myRootModels.values()) {
          each.dispose();
        }
        myModuleModel.dispose();
      }
    });
  }

  public long getCommitTime() {
    return myCommitTime;
  }

  public ModalityState getModalityStateForQuestionDialogs() {
    return ModalityState.NON_MODAL;
  }
}
