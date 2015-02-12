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
package com.jetbrains.python.commandInterface.command;

import com.intellij.openapi.module.Module;
import com.jetbrains.python.commandLineParser.CommandLineParseResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Command with arguments and options
 *
 * @author Ilya.Kazakevich
 */
public interface Command {


  /**
   * @return command name
   */
  @NotNull
  String getName();

  /**
   * @return Command readable help text
   */
  @Nullable
  String getHelp();


  /**
   * @return Information about command positional, unnamed {@link Argument arguments} (not options!)
   */
  @NotNull
  ArgumentsInfo getArgumentsInfo();

  /**
   * @return command options
   */
  @NotNull
  List<Option> getOptions();

  /**
   * Execute command
   *
   * @param module      module to execute command against
   * @param commandLine command's command line
   */
  void execute(@NotNull final Module module, @NotNull final CommandLineParseResult commandLine);
}
