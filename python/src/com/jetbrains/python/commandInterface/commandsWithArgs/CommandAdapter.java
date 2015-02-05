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
package com.jetbrains.python.commandInterface.commandsWithArgs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simple command implementation
 *
 * @author Ilya.Kazakevich
 */
public class CommandAdapter implements Command {
  @NotNull
  private final String myName;
  @Nullable
  private final String myHelp;
  @NotNull
  private final ArgumentsInfo myArgumentsInfo;

  /**
   * @param help          help text
   * @param name          command name
   * @param argumentsInfo arguments info
   */
  public CommandAdapter(@NotNull final String name, @Nullable final String help, @NotNull ArgumentsInfo argumentsInfo) {
    myName = name;
    myHelp = help;
    myArgumentsInfo = argumentsInfo;
  }

  /**
   * @return command name
   */
  @Override
  @NotNull
  public final String getName() {
    return myName;
  }


  @Override
  @Nullable
  public final String getHelp() {
    return myHelp;
  }


  @NotNull
  @Override
  public final ArgumentsInfo getArgumentsInfo() {
    return myArgumentsInfo;
  }
}
