package com.jetbrains.python.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyDependenciesConfigurable extends ModuleAwareProjectConfigurable implements OptionalConfigurable {
  private final Project myProject;

  public PyDependenciesConfigurable(Project project) {
    super(project, "Project Dependencies", "reference.settingsdialog.project.dependencies");
    myProject = project;
  }

  @NotNull
  @Override
  protected UnnamedConfigurable createModuleConfigurable(Module module) {
    return new PyModuleDependenciesConfigurable(module);
  }

  @Override
  public boolean needDisplay() {
    return ModuleManager.getInstance(myProject).getModules().length > 1;
  }
}
