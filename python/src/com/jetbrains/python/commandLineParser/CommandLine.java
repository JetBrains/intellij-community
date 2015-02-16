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

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.WordWithPosition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Command line getCommandLineInfo result.
 * It consists of command itself and its parts.
 * Each part may be {@link com.jetbrains.python.commandLineParser.CommandLinePartType#ARGUMENT argument} or
 * {@link com.jetbrains.python.commandLineParser.CommandLinePartType#OPTION option} or something else.
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLine {
  @NotNull
  private final List<CommandLinePart> myParts = new ArrayList<CommandLinePart>();
  @NotNull
  private final WordWithPosition myCommand;

  public CommandLine(
    @NotNull final WordWithPosition command,
    @NotNull final Collection<CommandLinePart> parts) {
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

  @NotNull
  public List<CommandLinePart> getParts() {
    return Collections.unmodifiableList(myParts);
  }

  /**
   * @return all command parts as text (actually by bindning them back together)
   */
  @NotNull
  public String getPartsAsText() {
    final StringBuilder builder = new StringBuilder();
    int lastPosition = 0;
    for (final CommandLinePart part : getParts()) {
      final WordWithPosition partWord = part.getWord();
      if (lastPosition != partWord.getFrom()) {
        builder.append(' ');
      }
      builder.append(partWord.getText());
      lastPosition = partWord.getTo();
    }
    return StringUtil.trim(builder.toString());
  }
}
