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
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;

public class MavenDefaultModifiableModelsProvider extends MavenBaseModifiableModelsProvider {
  private final Project myProject;
  private final LibraryTable.ModifiableModel myLibrariesModel;
  private volatile long myCommitTime;

  public MavenDefaultModifiableModelsProvider(Project project) {
    myProject = project;
    myLibrariesModel = ProjectLibraryTable.getInstance(myProject).getModifiableModel();
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

    MavenUtil.invokeAndWait(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            throw new UnsupportedOperationException();
          }
        });
      }
    });
    new WriteAction() {
      protected void run(Result result) throws Throwable {
        ((ProjectRootManagerEx)ProjectRootManager.getInstance(myProject)).mergeRootsChangesDuring(new Runnable() {
          public void run() {
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
          }
        });
      }
    }.execute();

    myCommitTime = System.currentTimeMillis() - before;
  }

  public long getCommitTime() {
    return myCommitTime;
  }

  public ModalityState getModalityStateForQuestionDialogs() {
    return ModalityState.NON_MODAL;
  }
}
