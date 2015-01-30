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
package com.jetbrains.python.commandInterface.commandsWithArgs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Information about command and its arguments to save in history / pass to execution etc.
 *
 * @author Ilya.Kazakevich
 */
public class CommandExecutionInfo {
  /**
   * Command and arguments separator.
   */
  private static final String SEPARATOR = " ";
  @NotNull
  private final String myCommandName;
  @NotNull
  private final String[] myArguments;

  /**
   * @param commandName command
   * @param arguments   its arguments
   */
  public CommandExecutionInfo(@NotNull final String commandName, @NotNull final String... arguments) {
    myCommandName = commandName;
    myArguments = arguments.clone();
  }

  /**
   * @return command
   */
  @NotNull
  public String getCommandName() {
    return myCommandName;
  }

  /**
   * @return command arguments
   */
  @NotNull
  public String[] getArguments() {
    return myArguments.clone();
  }

  /**
   * @return command in format "command arg1 arg2". Opposite to {@link #fromString(String)}
   * @see #fromString(String)
   */
  @NotNull
  public String toString() {
    // TODO: What if command or argument has space in it? Escape somehow!
    return StringUtil.join(ArrayUtil.mergeArrays(new String[]{myCommandName}, myArguments), SEPARATOR);
  }

  /**
   * @param stringToUnserialize string created by {@link #toString()}
   * @return command parsed from string
   * @see #toString()
   */
  @Nullable
  public static CommandExecutionInfo fromString(@NotNull final String stringToUnserialize) {
    // TODO: What if command or argument has space in it? Escape somehow!
    final List<String> strings = StringUtil.split(stringToUnserialize, SEPARATOR);
    if (strings.isEmpty()) {
      return null;
    }
    return new CommandExecutionInfo(strings.get(0), ArrayUtil.toStringArray(strings.subList(1, strings.size())));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CommandExecutionInfo)) return false;

    CommandExecutionInfo info = (CommandExecutionInfo)o;

    if (!Arrays.equals(myArguments, info.myArguments)) return false;
    if (!myCommandName.equals(info.myCommandName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myCommandName.hashCode();
    result = 31 * result + Arrays.hashCode(myArguments);
    return result;
  }
}
