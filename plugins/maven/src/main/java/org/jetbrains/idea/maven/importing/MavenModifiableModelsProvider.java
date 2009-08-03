package org.jetbrains.idea.maven.importing;

import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.idea.maven.project.MavenModelsProvider;

public interface MavenModifiableModelsProvider extends MavenModelsProvider {
  ModifiableModuleModel getModuleModel();

  ModifiableRootModel getRootModel(Module module);

  ModifiableFacetModel getFacetModel(Module module);

  Library[] getAllLibraries();

  Library getLibraryByName(String name);

  Library createLibrary(String name);

  void removeLibrary(Library library);

  Library.ModifiableModel getLibraryModel(Library library);

  void commit();

  long getCommitTime();

  ModalityState getModalityStateForQuestionDialogs();
}