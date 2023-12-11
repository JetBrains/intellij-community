/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  @Override
  protected Configurable createModuleConfigurable(@NotNull Module module) {
    return new PyIntegratedToolsConfigurable(module);
  }

  @Nullable
  @Override
  protected UnnamedConfigurable createDefaultProjectConfigurable() {
    return new PyIntegratedToolsConfigurable();
  }
}
