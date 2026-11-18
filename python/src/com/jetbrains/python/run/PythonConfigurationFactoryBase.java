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
package com.jetbrains.python.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModelKt;
import com.intellij.python.sdk.backend.PythonInterpreter;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class PythonConfigurationFactoryBase extends ConfigurationFactory {
  protected PythonConfigurationFactoryBase(@NotNull ConfigurationType type) {
    super(type);
  }

  /** Outside PyCharm, a Python run configuration is offered only when a Python project of {@code project} has an interpreter. */
  @Override
  public boolean isApplicable(@NotNull Project project) {
    if (PlatformUtils.isPyCharm()) return true;
    Set<PythonInterpreter> interpreters = EvoPyProjectModelKt.pythonInterpretersIfReady(project);
    return interpreters != null && !interpreters.isEmpty();
  }

  @Override
  public boolean isEditableInDumbMode() {
    return true;
  }
}
