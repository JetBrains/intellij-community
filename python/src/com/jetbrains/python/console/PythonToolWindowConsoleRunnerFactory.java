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
package com.jetbrains.python.console;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;

import java.util.Map;

/**
 * @author traff
 */
public class PythonToolWindowConsoleRunnerFactory extends PydevConsoleRunnerFactory {
  @Override
  protected PydevConsoleRunner createConsoleRunner(Project project,
                                                   Sdk sdk,
                                                   String workingDir,
                                                   Map<String, String> envs, PyConsoleType consoleType, String ... setupFragment) {
    return new PythonToolWindowConsoleRunner(project, sdk, consoleType, workingDir, envs, setupFragment);
  }
}
