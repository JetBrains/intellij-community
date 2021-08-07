// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;


public class BuildoutModulesConfigurable extends ModuleAwareProjectConfigurable implements NonDefaultProjectConfigurable {
  public BuildoutModulesConfigurable(Project project) {
    super(project, PyBundle.message("configurable.BuildoutModulesConfigurable.display.name"), "reference-python-buildout");
  }

  @NotNull
  @Override
  protected Configurable createModuleConfigurable(Module module) {
    return new BuildoutConfigurable(module);
  }
}
