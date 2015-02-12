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
package com.jetbrains.python.commandLineParser.optParse;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.WordWithPosition;
import com.jetbrains.python.commandInterface.command.Command;
import com.jetbrains.python.commandInterface.command.Option;
import com.jetbrains.python.commandInterface.command.OptionArgumentInfo;
import com.jetbrains.python.commandLineParser.CommandLineParseResult;
import com.jetbrains.python.commandLineParser.CommandLineParser;
import com.jetbrains.python.commandLineParser.CommandLinePartType;
import com.jetbrains.python.commandLineParser.MalformedCommandLineException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
  /**
   * Supported option parsers (option types, actually)
   */
  @NotNull
  private static final OptionParser[] OPTION_PARSERS = {new LongOptionParser(), new ShortOptionParser()};

  /**
   * CommandName -> Options
   */
  @NotNull
  private final Map<String, List<Option>> myCommandOptions = new HashMap<String, List<Option>>();

  /**
   * @param commands commands (needed to parse out options)
   */
  public OptParseCommandLineParser(@NotNull final Iterable<? extends Command> commands) {
    for (final Command command : commands) {
      myCommandOptions.put(command.getName(), command.getOptions());
    }
  }

  @NotNull
  @Override
  public CommandLineParseResult parse(@NotNull final List<WordWithPosition> commandLineParts) throws MalformedCommandLineException {
    final Deque<WordWithPosition> parts = new ArrayDeque<WordWithPosition>(commandLineParts);
    if (parts.isEmpty()) {
      throw new MalformedCommandLineException("No command provided");
    }
    final WordWithPosition command = parts.pop();
    final List<Option> options;
    if (myCommandOptions.containsKey(command.getText())) {
      options = myCommandOptions.get(command.getText());
    }
    else {
      options = Collections.emptyList();
    }


    final List<Pair<CommandLinePartType, WordWithPosition>> resultParts = new ArrayList<Pair<CommandLinePartType, WordWithPosition>>();
    if (isOption(command)) {
      throw new MalformedCommandLineException("Command can't start with option prefix");
    }

    int optionArgumentsLeft = 0; // If option has N arguments, then next N arguments belong to this option
    for (final WordWithPosition part : parts) {
      if (optionArgumentsLeft > 0) {
        optionArgumentsLeft--;
        resultParts.add(Pair.create(CommandLinePartType.OPTION_ARGUMENT, part));
        continue;
      }
      if (isOption(part)) {
        // This is option!
        final Pair<Option, String> optionAndValue = findOptionAndValue(options, part.getText());
        if (optionAndValue != null) {
          final Option option = optionAndValue.first;
          final Pair<Integer, OptionArgumentInfo> argumentAndQuantity = option.getArgumentAndQuantity();
          if (argumentAndQuantity != null) {
            optionArgumentsLeft = argumentAndQuantity.first;
          }
          final String optionArgumentValue = optionAndValue.second;
          if (optionArgumentValue != null) {
            // We found argument
            optionArgumentsLeft--;
            final String optionArgumentText =
              part.getText().substring(0, part.getText().length() - optionArgumentValue.length());
            final WordWithPosition optionPart =
              new WordWithPosition(optionArgumentText, part.getFrom(), part.getFrom() + optionArgumentText.length());
            final WordWithPosition valuePart = new WordWithPosition(optionArgumentValue, optionPart.getTo(), part.getTo());
            resultParts.add(Pair.create(CommandLinePartType.OPTION, optionPart));
            resultParts.add(Pair.create(CommandLinePartType.OPTION_ARGUMENT, valuePart));
          }
          else {
            resultParts.add(Pair.create(CommandLinePartType.OPTION, part));
          }
        }
        else {
          resultParts.add(Pair.create(CommandLinePartType.UNKNOWN, part));
        }
      }
      else {
        resultParts.add(Pair.create(CommandLinePartType.ARGUMENT, part));
      }
    }
    return new CommandLineParseResult(command, resultParts);
  }

  /**
   * Parse out option and its value iterating through the parsers
   *
   * @param options all available options
   * @param text    text believed to be an option like "--foo=123"
   * @return [option->argument_value] pair or null if provided text is not an option. ArgValue may also be null if not provided
   * @see com.jetbrains.python.commandLineParser.optParse.OptionParser#findOptionAndValue(java.util.List, String)
   */
  @Nullable
  private static Pair<Option, String> findOptionAndValue(@NotNull final List<Option> options, @NotNull final String text) {
    for (final OptionParser parser : OPTION_PARSERS) {
      final Pair<Option, String> optionAndValue = parser.findOptionAndValue(options, text);
      if (optionAndValue != null) {
        return optionAndValue;
      }
    }
    return null;
  }

  /**
   * @param command command to check
   * @return if command is option or not
   */
  private static boolean isOption(@NotNull final WordWithPosition command) {
    return command.getText().startsWith("-");
  }
}
