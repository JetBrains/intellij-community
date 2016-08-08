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
package com.jetbrains.commandInterface.command;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Simple implementation of {@link Command}. It just structure that stores all info + {@link CommandExecutor} to execute commands.
 * It also delegates its execution to external {@link CommandExecutor}
 *
 * @author Ilya.Kazakevich
 */
public final class SimpleCommand implements Command {
  @NotNull
  private final String myName;
  @Nullable
  private final Help myHelp;
  @NotNull
  private final ArgumentsInfo myArgumentsInfo;
  @NotNull
  private final List<Option> myOptions = new ArrayList<>();
  @NotNull
  private final CommandExecutor myExecutor;


  /**
   *
   * @param name command name
   * @param help command help (if available)
   * @param argumentsInfo command arguments
   * @param executor engine to execute command
   * @param options command options
   */
  public SimpleCommand(@NotNull final String name,
                       @Nullable final Help help,
                       @NotNull final ArgumentsInfo argumentsInfo,
                       @NotNull final CommandExecutor executor,
                       @NotNull final Collection<Option> options) {
    myName = name;
    myHelp = help;
    myArgumentsInfo = argumentsInfo;
    myExecutor = executor;
    myOptions.addAll(options);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public Help getHelp(final boolean tryCutOutArguments) {
    if (!tryCutOutArguments || myHelp == null) {
      return myHelp;
    }
    // Cut out arguments like [] and <> from text
    final String newHelpString = myHelp.getHelpString().replaceAll("(\\[[^\\]]+\\]|<[^>]+>|\\.{3,}|^" + myName + ')', "").trim();
    return new Help(newHelpString, myHelp.getExternalHelpUrl());
  }

  @NotNull
  @Override
  public ArgumentsInfo getArgumentsInfo() {
    return myArgumentsInfo;
  }

  @NotNull
  @Override
  public List<Option> getOptions() {
    return Collections.unmodifiableList(myOptions);
  }

  @Override
  public void execute(@NotNull final String commandName,
                      @NotNull final Module module,
                      @NotNull final List<String> parameters,
                      @Nullable final ConsoleView consoleView) {
    myExecutor.execute(myName, module, parameters, consoleView);
  }

  @NotNull
  public CommandExecutor getExecutor() {
    return myExecutor;
  }

}
