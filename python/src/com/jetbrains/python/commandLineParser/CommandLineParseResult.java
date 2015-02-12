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
package com.jetbrains.python.commandLineParser;

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.WordWithPosition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Command line parse result.
 * It consists of command itself and its parts.
 * Each part may be {@link com.jetbrains.python.commandLineParser.CommandLinePartType#ARGUMENT argument} or
 * {@link com.jetbrains.python.commandLineParser.CommandLinePartType#OPTION option} or something else.
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineParseResult {
  @NotNull
  private final List<Pair<CommandLinePartType, WordWithPosition>> myParts = new ArrayList<Pair<CommandLinePartType, WordWithPosition>>();
  @NotNull
  private final WordWithPosition myCommand;

  public CommandLineParseResult(
    @NotNull final WordWithPosition command,
    @NotNull final Collection<Pair<CommandLinePartType, WordWithPosition>> parts) {
    myCommand = command;
    myParts.addAll(parts);
  }

  /**
   * @return command (i.e. "startapp" in "startapp my_app")
   */
  @NotNull
  public WordWithPosition getCommand() {
    return myCommand;
  }

  /**
   * @return list of parts in format [part_type, value].
   * For example (rm my_folder): [{@link com.jetbrains.python.commandLineParser.CommandLinePartType#ARGUMENT argument}, my_folder]
   */
  @NotNull
  public List<Pair<CommandLinePartType, WordWithPosition>> getParts() {
    return Collections.unmodifiableList(myParts);
  }

  /**
   * @return all command line parts with out of part information (just words and positions).
   * Note tha command itself is not part, only args and options are
   * @see #getParts()
   */
  @NotNull
  public Collection<WordWithPosition> getPartsNoType() {
    final Collection<WordWithPosition> result = new ArrayList<WordWithPosition>();
    for (final Pair<CommandLinePartType, WordWithPosition> part : myParts) {
      result.add(part.second);
    }
    return result;
  }
}
