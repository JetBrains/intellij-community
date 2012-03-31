package com.jetbrains.python.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyIntegratedToolsModulesConfigurable extends ModuleAwareProjectConfigurable implements NonDefaultProjectConfigurable {
  public PyIntegratedToolsModulesConfigurable(@NotNull Project project) {
    super(project, "Python Integrated Tools", "reference-python-integrated-tools");
  }

  @NotNull
  @Override
  protected Configurable createModuleConfigurable(@NotNull Module module) {
    return new PyIntegratedToolsConfigurable(module);
  }
}
