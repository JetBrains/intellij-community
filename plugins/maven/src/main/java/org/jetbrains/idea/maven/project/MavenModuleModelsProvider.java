package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;

public interface MavenModuleModelsProvider {
  ModifiableModuleModel getModuleModel();

  ModifiableRootModel getRootModel(Module module);

  void commit(ModifiableModuleModel modulModel, ModifiableRootModel[] rootModels);
}