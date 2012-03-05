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
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenModelsProvider;

public interface MavenModifiableModelsProvider extends MavenModelsProvider {
  ModifiableModuleModel getModuleModel();

  ModifiableRootModel getRootModel(Module module);

  ModifiableFacetModel getFacetModel(Module module);

  ModifiableArtifactModel getArtifactModel();

  PackagingElementResolvingContext getPackagingElementResolvingContext();

  ArtifactExternalDependenciesImporter getArtifactExternalDependenciesImporter();

  LibraryTable.ModifiableModel getProjectLibrariesModel();

  Library[] getAllLibraries();

  Library getLibraryByName(String name);

  Library createLibrary(String name);

  void removeLibrary(Library library);

  Library.ModifiableModel getLibraryModel(Library library);

  @NotNull
  String[] getLibraryUrls(@NotNull Library library, @NotNull OrderRootType type);

  void commit();

  void dispose();

  ModalityState getModalityStateForQuestionDialogs();
}
