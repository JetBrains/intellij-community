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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import org.jetbrains.annotations.NotNull;

/**
 * <i>To be deprecated. The part of the legacy implementation based on {@link GeneralCommandLine}.</i>
 */
public interface PythonCommandLineEnvironmentProvider {
  ExtensionPointName<PythonCommandLineEnvironmentProvider> EP_NAME =
    ExtensionPointName.create("Pythonid.pythonCommandLineEnvironmentProvider");

  void extendEnvironment(@NotNull Project project,
                         SdkAdditionalData data,
                         @NotNull GeneralCommandLine cmdLine,
                         PythonRunParams runParams);
}
