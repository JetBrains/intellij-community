// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.module.PyModuleService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


// Inherit in the module you are going to use it
@ApiStatus.Internal
public abstract class PyActiveSdkModuleConfigurable extends ModuleAwareProjectConfigurable<UnnamedConfigurable> {
  private final Project myProject;

  protected PyActiveSdkModuleConfigurable(Project project) {
    super(project, PyBundle.message("configurable.PyActiveSdkModuleConfigurable.python.interpreter.display.name"),
          "reference.settings.project.interpreter");
    myProject = project;
  }

  @Override
  protected final boolean isSuitableForModule(@NotNull Module module) {
    // One can't configure Python SDK for a random module as random module doesn't have a `baseDir`
    // which is a requirement for various SDK types.
    return PyModuleService.getInstance().isPythonModule(module);
  }

  @Override
  protected @NotNull UnnamedConfigurable createModuleConfigurable(Module module) {
    return new PyActiveSdkConfigurable(module);
  }

  @Override
  protected UnnamedConfigurable createDefaultProjectConfigurable() {
    return new PyActiveSdkConfigurable(myProject);
  }
}
