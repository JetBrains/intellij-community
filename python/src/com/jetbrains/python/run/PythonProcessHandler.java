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
package com.jetbrains.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * @author traff
 */
public class PythonProcessHandler extends KillableColoredProcessHandler {
  public static final boolean SOFT_KILL_ON_WIN = Registry.is("kill.windows.processes.softly", false);

  public PythonProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    this(commandLine, SOFT_KILL_ON_WIN);
  }

  public PythonProcessHandler(@NotNull GeneralCommandLine commandLine, boolean softKillOnWin) throws ExecutionException {
    super(commandLine, softKillOnWin);
  }

  public PythonProcessHandler(Process process, @NotNull String commandLine, @NotNull Charset charset) {
    super(process, commandLine, charset);
  }

  @Override
  protected boolean shouldDestroyProcessRecursively() {
    return true;
  }
}
