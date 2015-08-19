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

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ConsoleViewImpl} wrapper to be used by simple {@link ProcessWithConsoleRunner}s: it allows handler to be attached to console
 *
 * @author Ilya.Kazakevich
 */
public class SimpleProcessRunnerConsole extends ConsoleViewImpl {
  @NotNull
  private final ProcessListener myHandler;

  /**
   * @param project         project
   * @param processListener listener passed to {@link ProcessWithConsoleRunner} by {@link PyProcessWithConsoleTestTask}.
   *                        Will be attached to process
   */
  public SimpleProcessRunnerConsole(@NotNull final Project project, @NotNull final ProcessListener processListener) {
    super(project, false);
    myHandler = processListener;
  }

  @Override
  public void attachToProcess(final ProcessHandler processHandler) {
    super.attachToProcess(processHandler);
    processHandler.addProcessListener(myHandler);
  }
}
