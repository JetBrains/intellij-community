package com.jetbrains.python.buildout;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class BuildoutModulesConfigurable extends ModuleAwareProjectConfigurable implements NonDefaultProjectConfigurable {
  public BuildoutModulesConfigurable(Project project) {
    super(project, "Buildout Support", "reference-python-buildout");
  }

  @NotNull
  @Override
  protected Configurable createModuleConfigurable(Module module) {
    return new BuildoutConfigurable(module);
  }
}
