/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;

/**
 * @author Sergey.Malenkov
 */
public final class PyDependenciesConfigurableProvider extends ConfigurableProvider {
  private final Project myProject;

  public PyDependenciesConfigurableProvider(Project project) {
    myProject = project;
  }

  @Override
  public boolean canCreateConfigurable() {
    return ModuleManager.getInstance(myProject).getModules().length > 1;
  }

  @Override
  public Configurable createConfigurable() {
    return new PyDependenciesConfigurable(myProject);
  }
}
