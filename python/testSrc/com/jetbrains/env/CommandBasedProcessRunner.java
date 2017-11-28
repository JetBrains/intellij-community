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

import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.jetbrains.commandInterface.command.SimpleCommand;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * {@link ProcessWithConsoleRunner} that runs {@link SimpleCommand}.
 * It uses {@link SimpleProcessRunnerConsole} so you have access to console
 *
 * @author Ilya.Kazakevich
 * @see PyProcessWithConsoleTestTask
 * @see com.jetbrains.commandInterface.command
 */
public class CommandBasedProcessRunner extends ProcessWithConsoleRunner {
  @NotNull
  private final SimpleCommand myCommand;
  @NotNull
  private final Module myModule;
  @NotNull
  private final List<String> myParameters;

  /**
   * @param command    command to run
   * @param module     module to run on
   * @param parameters command parameters
   */
  public CommandBasedProcessRunner(@NotNull final SimpleCommand command,
                                   @NotNull final Module module,
                                   @NotNull final String... parameters) {
    myCommand = command;
    myModule = module;
    myParameters = Arrays.asList(parameters);
  }

  @Override
  void runProcess(@NotNull final String sdkPath,
                  @NotNull final Project project,
                  @NotNull final ProcessListener processListener,
                  @NotNull final String tempWorkingPath) {
    myConsole = new SimpleProcessRunnerConsole(project, processListener);
    myCommand.execute(myCommand.getName(), myModule, myParameters, myConsole);
  }
}
