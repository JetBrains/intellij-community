// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyIntegratedToolsModulesConfigurable extends ModuleAwareProjectConfigurable {
  public PyIntegratedToolsModulesConfigurable(@NotNull Project project) {
    super(project, PyBundle.message("configurable.PyIntegratedToolsModulesConfigurable.display.name"), "reference-python-integrated-tools");
  }

  @Override
  protected @NotNull Configurable createModuleConfigurable(@NotNull Module module) {
    return new PyIntegratedToolsConfigurable(module);
  }

  @Override
  protected @Nullable UnnamedConfigurable createDefaultProjectConfigurable() {
    return new PyIntegratedToolsConfigurable();
  }
}
