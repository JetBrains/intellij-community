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
package com.jetbrains.env;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.PythonTask;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ProcessWithConsoleRunner} that runs {@link PythonTask}.
 * It uses {@link SimpleProcessRunnerConsole} so you have access to console
 *
 * @author Ilya.Kazakevich
 * @see PyProcessWithConsoleTestTask
 * @see PythonTask
 */
public class TaskBasedProcessRunner extends ProcessWithConsoleRunner {
  @NotNull
  private final PythonTask myTask;

  public TaskBasedProcessRunner(@NotNull final PythonTask task) {
    myTask = task;
  }

  @Override
  void runProcess(@NotNull final String sdkPath, @NotNull final Project project, @NotNull final ProcessListener processListener)
    throws ExecutionException {
    myConsole = new SimpleProcessRunnerConsole(project, processListener);
    myTask.run(myConsole);
  }
}
