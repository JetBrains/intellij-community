package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;

public class MavenUIModuleModelsProvider implements MavenModuleModelsProvider {
  private final ModifiableModuleModel myModel;
  private final ModulesProvider myModulesProvider;

  public MavenUIModuleModelsProvider(ModifiableModuleModel model, ModulesProvider modulesProvider) {
    myModel = model;
    myModulesProvider = modulesProvider;
  }

  public ModifiableModuleModel getModuleModel() {
    return myModel;
  }

  public ModuleRootModel getRootModel(Module module) {
    return myModulesProvider.getRootModel(module);
  }

  public ModifiableRootModel getModifiableRootModel(Module module) {
    return (ModifiableRootModel)myModulesProvider.getRootModel(module);
  }

  public void commit(ModifiableModuleModel modulModel, ModifiableRootModel[] rootModels) {
  }
}
