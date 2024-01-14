// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;


public class PyDependenciesConfigurable extends ModuleAwareProjectConfigurable {
  public PyDependenciesConfigurable(Project project) {
    super(project, PyBundle.message("configurable.PyDependenciesConfigurable.display.name"), "reference.settingsdialog.project.dependencies");
  }

  @NotNull
  @Override
  protected UnnamedConfigurable createModuleConfigurable(Module module) {
    return new PyModuleDependenciesConfigurable(module);
  }
}
