package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ProjectRootManager;

public class DefaultMavenModuleModelsProvider implements MavenModuleModelsProvider {
  private final Project myProject;

  public DefaultMavenModuleModelsProvider(Project project) {
    myProject = project;
  }

  public ModifiableModuleModel getModuleModel() {
    return ModuleManager.getInstance(myProject).getModifiableModel();
  }

  public ModuleRootModel getRootModel(Module module) {
    return ModuleRootManager.getInstance(module);
  }

  public ModifiableRootModel getModifiableRootModel(Module module) {
    return ModuleRootManager.getInstance(module).getModifiableModel();
  }

  public void commit(ModifiableModuleModel modulModel, ModifiableRootModel[] rootModels) {
    ProjectRootManager.getInstance(myProject).multiCommit(modulModel, rootModels);
  }
}
