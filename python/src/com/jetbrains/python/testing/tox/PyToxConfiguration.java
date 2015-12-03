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
package com.jetbrains.python.testing.tox;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Ilya.Kazakevich
 */
final class PyToxConfiguration extends AbstractPythonRunConfiguration<PyToxConfiguration> {

  @NotNull
  private final Project myProject;

  PyToxConfiguration(@NotNull final PyToxConfigurationFactory factory, @NotNull final Project project) {
    super(project, factory);
    myProject = project;
  }


  @Override
  protected SettingsEditor<PyToxConfiguration> createConfigurationEditor() {
    return new PyToxConfigurationSettings(myProject);
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment environment) {
    getWorkingDirectory();
    return new PyToxCommandLineState(this, environment);
  }
}
