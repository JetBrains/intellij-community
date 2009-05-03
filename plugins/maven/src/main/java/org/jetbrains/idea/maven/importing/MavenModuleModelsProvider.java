package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;

public interface MavenModuleModelsProvider {
  ModifiableModuleModel getModuleModel();

  ModuleRootModel getRootModel(Module module);

  ModifiableRootModel getModifiableRootModel(Module module);

  void commit(ModifiableModuleModel modulModel, ModifiableRootModel[] rootModels);
}