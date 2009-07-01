package org.jetbrains.idea.maven.importing;

import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;

import java.util.Map;

public abstract class MavenBaseModifiableModelsProvider implements MavenModifiableModelsProvider {
  protected ModifiableModuleModel myModuleModel;
  protected Map<Module, ModifiableRootModel> myRootModels = new THashMap<Module, ModifiableRootModel>();
  protected Map<Module, ModifiableFacetModel> myFacetModels = new THashMap<Module, ModifiableFacetModel>();
  protected Map<Library, Library.ModifiableModel> myLibraryModels = new THashMap<Library, Library.ModifiableModel>();

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

  public Library.ModifiableModel getLibraryModel(Library library) {
    Library.ModifiableModel result = myLibraryModels.get(library);
    if (result == null) {
      result = doGetLibraryModel(library);
      myLibraryModels.put(library, result);
    }
    return result;
  }

  protected abstract ModifiableModuleModel doGetModuleModel();

  protected abstract ModifiableRootModel doGetRootModel(Module module);

  protected abstract ModifiableFacetModel doGetFacetModel(Module module);

  protected abstract Library.ModifiableModel doGetLibraryModel(Library library);

  public Module[] getModules() {
    return getModuleModel().getModules();
  }

  public VirtualFile[] getContentRoots(Module module) {
    return getRootModel(module).getContentRoots();
  }
}
