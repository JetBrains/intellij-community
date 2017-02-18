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
package com.jetbrains.edu.learning.checker;

import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StudyExecutor {
  LanguageExtension<StudyExecutor> INSTANCE = new LanguageExtension<>("Edu.StudyExecutor");

  @Nullable
  Sdk findSdk(@NotNull final Project project);

  StudyTestRunner getTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir);

  RunContentExecutor getExecutor(@NotNull final Project project, @NotNull final ProcessHandler handler);

  void setCommandLineParameters(@NotNull final GeneralCommandLine cmd,
                                @NotNull final Project project,
                                @NotNull final String filePath,
                                @NotNull final String sdkPath,
                                @NotNull final Task currentTask);

  void showNoSdkNotification(@NotNull final Project project);
}
