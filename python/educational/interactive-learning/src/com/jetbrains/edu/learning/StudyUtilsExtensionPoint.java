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
package com.jetbrains.edu.learning;

import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.course.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StudyUtilsExtensionPoint {
  ExtensionPointName<StudyUtilsExtensionPoint> EP_NAME = ExtensionPointName.create("Edu.StudyUtils");

  @Nullable
  abstract Sdk findSdk(@NotNull final Project project);

  abstract String getLinkToTutorial();

  abstract StudyTestRunner getTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir);

  public RunContentExecutor getExecutor(@NotNull final Project project, @NotNull final ProcessHandler handler);

  public void setCommandLineParameters(@NotNull final GeneralCommandLine cmd,
                                       @NotNull final Project project,
                                       @NotNull final String filePath,
                                       @NotNull final String pythonPath,
                                       @NotNull final Task currentTask);
}
