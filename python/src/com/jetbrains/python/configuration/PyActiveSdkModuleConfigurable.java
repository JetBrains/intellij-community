// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


// Inherit in the module you are going to use it
@ApiStatus.Internal
public abstract class PyActiveSdkModuleConfigurable extends ModuleAwareProjectConfigurable {
  private final Project myProject;

  protected PyActiveSdkModuleConfigurable(Project project) {
    super(project, PyBundle.message("configurable.PyActiveSdkModuleConfigurable.python.interpreter.display.name"), "reference.settings.project.interpreter");
    myProject = project;
  }

  @NotNull
  @Override
  protected UnnamedConfigurable createModuleConfigurable(Module module) {
    return new PyActiveSdkConfigurable(module);
  }

  @Override
  protected UnnamedConfigurable createDefaultProjectConfigurable() {
    return new PyActiveSdkConfigurable(myProject);
  }
}
