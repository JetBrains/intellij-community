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
package com.jetbrains.python.commandInterface.commandBasedRangeDriver;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.commandInterface.command.Command;
import com.jetbrains.python.commandInterface.rangeBasedPresenter.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Executes commands according to {@link Executor} contract
 *
 * @author Ilya.Kazakevich
 */
final class CommandExecutor implements Executor {
  @NotNull
  private final Command myCommand;
  @NotNull
  private final Module myModule;
  @NotNull
  private final String[] myArguments;

  /**
   * @param command       command to execute
   * @param module        module to execute against
   * @param argumentsLine all command arguments as testline
   */
  CommandExecutor(@NotNull final Command command, @NotNull final Module module, @NotNull final String argumentsLine) {
    myCommand = command;
    myModule = module;
    myArguments = (StringUtil.isEmpty(argumentsLine) ? ArrayUtil.EMPTY_STRING_ARRAY : argumentsLine.split(" "));
  }

  @Nullable
  @Override
  public String getExecutionDescription() {
    return myCommand.getHelp();
  }

  @Override
  public void execute() {
    myCommand.execute(myModule, Arrays.asList(myArguments));
  }
}
