package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectLibrariesConfigurable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.facet.ModifiableFacetModel;

public class MavenUIModifiableModelsProvider extends MavenBaseModifiableModelsProvider {
  private final ModifiableModuleModel myModel;
  private final ModulesProvider myModulesProvider;
  private final LibrariesModifiableModel myLibrariesModel;

  public MavenUIModifiableModelsProvider(Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
    myModel = model;
    myModulesProvider = modulesProvider;

    ProjectLibrariesConfigurable configurable = ProjectLibrariesConfigurable.getInstance(project);
    myLibrariesModel = (LibrariesModifiableModel)configurable.getModelProvider(true).getModifiableModel();
  }

  @Override
  protected ModifiableModuleModel doGetModuleModel() {
    return myModel;
  }

  @Override
  protected ModifiableRootModel doGetRootModel(Module module) {
    return (ModifiableRootModel)myModulesProvider.getRootModel(module);
  }

  @Override
  protected ModifiableFacetModel doGetFacetModel(Module module) {
    return (ModifiableFacetModel)myModulesProvider.getFacetModel(module);
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
  }

  public void dispose() {
  }

  public long getCommitTime() {
    return 0;
  }

  public ModalityState getModalityStateForQuestionDialogs() {
    return ModalityState.defaultModalityState();
  }
}
