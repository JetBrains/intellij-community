// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.intellij.application.options.ModuleAwareProjectConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.module.PyModuleService;
import com.jetbrains.python.sdk.BasePySdkExtKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


// Inherit in the module you are going to use it
@ApiStatus.Internal
public abstract class PyActiveSdkModuleConfigurable extends ModuleAwareProjectConfigurable<UnnamedConfigurable> {
  public static final String CONFIGURABLE_ID = "com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable";

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
    // All python modules do have it.
    // But some IDEs might not want to see Python SDK configs for their modules even when they have `baseDir`.
    // Those might disable the registry key.
    return PyModuleService.getInstance().isPythonModule(module) ||
           (Registry.is("python.show.modules.with.base.dir") && BasePySdkExtKt.getBaseDir(module) != null);
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
