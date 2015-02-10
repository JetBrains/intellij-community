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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// TODO: Support options and their arguments

/**
 * <p>
 * <a href="https://docs.python.org/2/library/optparse.html">Optparse</a>-based commandline parses.
 * According to optparse manual, commandline should look like:
 * <pre>command arg1 arg2 --long-bool-opt -s --another-opt opt_arg1 opt_arg2 --yet-another-opt=opt_arg3 arg4 </pre>.
 * You should understand difference between argument, long option, short option, and option argument before using this class.
 * It is documented in optparse manual.
 * </p>
 * <p>
 * This class provides not only args and options (like many other parsers do), but also <strong>position in command line</strong>
 * which may be useful when you want to mark argument somehow.
 * </p>
 *
 * @author Ilya.Kazakevich
 */
public final class OptParseCommandLineParser implements CommandLineParser {
  @NotNull
  @Override
  public CommandLineParseResult parse(@NotNull final List<WordWithPosition> commandLineParts) throws MalformedCommandLineException {
    final Deque<WordWithPosition> parts = new ArrayDeque<WordWithPosition>(commandLineParts);
    if (parts.isEmpty()) {
      throw new MalformedCommandLineException("No command provided");
    }
    final WordWithPosition command = parts.pop();
    final List<Pair<CommandLinePartType, WordWithPosition>> resultParts = new ArrayList<Pair<CommandLinePartType, WordWithPosition>>();
    if (command.getText().startsWith("-")) {
      throw new MalformedCommandLineException("Command can't start with option prefix");
    }

    // TODO: Support option arguments!!! Not only bool options exist! Check nargs!
    for (final WordWithPosition part : parts) {
      if (part.getText().startsWith("-")) {
        // This is option!
        resultParts.add(Pair.create(CommandLinePartType.OPTION, part));
      }
      else {
        // TODO: Check optopn argument!
        resultParts.add(Pair.create(CommandLinePartType.ARGUMENT, part));
      }
    }
    return new CommandLineParseResult(command, resultParts);
  }
}
