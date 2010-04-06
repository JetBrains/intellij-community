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
import com.intellij.facet.FacetModel;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Collection;

public abstract class MavenBaseModifiableModelsProvider implements MavenModifiableModelsProvider {
  protected ModifiableModuleModel myModuleModel;
  protected Map<Module, ModifiableRootModel> myRootModels = new THashMap<Module, ModifiableRootModel>();
  protected Map<Module, ModifiableFacetModel> myFacetModels = new THashMap<Module, ModifiableFacetModel>();
  protected Map<Library, Library.ModifiableModel> myLibraryModels = new THashMap<Library, Library.ModifiableModel>();
  protected ModifiableArtifactModel myArtifactModel;
  protected final Project myProject;
  private MavenBaseModifiableModelsProvider.MyPackagingElementResolvingContext myPackagingElementResolvingContext;
  private final ArtifactExternalDependenciesImporter myArtifactExternalDependenciesImporter;

  public MavenBaseModifiableModelsProvider(Project project) {
    myProject = project;
    myArtifactExternalDependenciesImporter = new ArtifactExternalDependenciesImporter();
  }

  public ModifiableModuleModel getModuleModel() {
    if (myModuleModel == null) {
      myModuleModel = doGetModuleModel();
    }
    return myModuleModel;
  }

  public ModifiableRootModel getRootModel(Module module) {
    ModifiableRootModel result = myRootModels.get(module);
    if (result == null) {
      result = doGetRootModel(module);
      myRootModels.put(module, result);
    }
    return result;
  }

  public ModifiableFacetModel getFacetModel(Module module) {
    ModifiableFacetModel result = myFacetModels.get(module);
    if (result == null) {
      result = doGetFacetModel(module);
      myFacetModels.put(module, result);
    }
    return result;
  }

  public ModifiableArtifactModel getArtifactModel() {
    if (myArtifactModel == null) {
      myArtifactModel = doGetArtifactModel();
    }
    return myArtifactModel;
  }

  public PackagingElementResolvingContext getPackagingElementResolvingContext() {
    if (myPackagingElementResolvingContext == null) {
      myPackagingElementResolvingContext = new MyPackagingElementResolvingContext();
    }
    return myPackagingElementResolvingContext;
  }

  public ArtifactExternalDependenciesImporter getArtifactExternalDependenciesImporter() {
    return myArtifactExternalDependenciesImporter;
  }

  public Library.ModifiableModel getLibraryModel(Library library) {
    Library.ModifiableModel result = myLibraryModels.get(library);
    if (result == null) {
      result = doGetLibraryModel(library);
      myLibraryModels.put(library, result);
    }
    return result;
  }

  protected abstract ModifiableArtifactModel doGetArtifactModel();

  protected abstract ModifiableModuleModel doGetModuleModel();

  protected abstract ModifiableRootModel doGetRootModel(Module module);

  protected abstract ModifiableFacetModel doGetFacetModel(Module module);

  protected abstract Library.ModifiableModel doGetLibraryModel(Library library);

  public Module[] getModules() {
    return getModuleModel().getModules();
  }

  protected void processExternalArtifactDependencies() {
    myArtifactExternalDependenciesImporter.applyChanges(getArtifactModel(), getPackagingElementResolvingContext());
  }

  public VirtualFile[] getContentRoots(Module module) {
    return getRootModel(module).getContentRoots();
  }

  private class MyPackagingElementResolvingContext implements PackagingElementResolvingContext {
    private final ModulesProvider myModulesProvider = new MavenModulesProvider();
    private final MavenFacetsProvider myFacetsProvider = new MavenFacetsProvider();

    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public ArtifactModel getArtifactModel() {
      return MavenBaseModifiableModelsProvider.this.getArtifactModel();
    }

    @NotNull
    public ModulesProvider getModulesProvider() {
      return myModulesProvider;
    }

    @NotNull
    public FacetsProvider getFacetsProvider() {
      return myFacetsProvider;
    }

    public Library findLibrary(@NotNull String level, @NotNull String libraryName) {
      if (level.equals(LibraryTablesRegistrar.PROJECT_LEVEL)) {
        return getLibraryByName(libraryName);
      }
      final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, myProject);
      return table != null ? table.getLibraryByName(libraryName) : null;
    }

  }

  private class MavenModulesProvider implements ModulesProvider {
    @NotNull
    public Module[] getModules() {
      return getModuleModel().getModules();
    }

    public Module getModule(String name) {
      return getModuleModel().findModuleByName(name);
    }

    public ModuleRootModel getRootModel(@NotNull Module module) {
      return MavenBaseModifiableModelsProvider.this.getRootModel(module);
    }

    public FacetModel getFacetModel(@NotNull Module module) {
      return MavenBaseModifiableModelsProvider.this.getFacetModel(module);
    }
  }

  private class MavenFacetsProvider implements FacetsProvider {
    @NotNull
    public Facet[] getAllFacets(Module module) {
      return getFacetModel(module).getAllFacets();
    }

    @NotNull
    public <F extends Facet> Collection<F> getFacetsByType(Module module, FacetTypeId<F> type) {
      return getFacetModel(module).getFacetsByType(type);
    }

    public <F extends Facet> F findFacet(Module module, FacetTypeId<F> type, String name) {
      return getFacetModel(module).findFacet(type, name);
    }
  }
}
