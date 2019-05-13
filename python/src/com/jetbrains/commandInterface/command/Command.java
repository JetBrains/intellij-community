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
package com.jetbrains.commandInterface.command;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Command with arguments and options
 *
 * @author Ilya.Kazakevich
 */
public interface Command extends CommandExecutor {


  /**
   * @return command name
   */
  @NotNull
  String getName();

  /**
   * @param tryCutOutArguments Try to remove information about arguments from help text (i.e. "[file] removes file" -> "removes file").
   *                           Command may or may not support it.
   *                           It should ignore argument if it does not know how to cut out argument info.
   * @return Command help
   */
  @Nullable
  Help getHelp(boolean tryCutOutArguments);


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

}
