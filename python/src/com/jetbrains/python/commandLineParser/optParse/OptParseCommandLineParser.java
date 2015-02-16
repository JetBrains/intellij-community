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
import com.jetbrains.python.WordWithPosition;
import com.jetbrains.python.commandLineParser.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
   * Short (-o) and long (--option) strategies are used here
   */

  private static final OptionParser[] OPTION_PARSERS = {new ShortOptionParser(), new LongOptionParser()};


  @NotNull
  @Override
  public CommandLine parse(@NotNull final String commandLineText) throws MalformedCommandLineException {
    final Deque<WordWithPosition> parts = new ArrayDeque<WordWithPosition>(WordWithPosition.splitText(commandLineText));
    if (parts.isEmpty()) {
      throw new MalformedCommandLineException("No command provided");
    }
    final WordWithPosition command = parts.pop();

    final List<CommandLinePart> optionsAndArguments = new ArrayList<CommandLinePart>();

    for (final WordWithPosition part : parts) {
      final Pair<String, String> optionTextAndName = findOptionTextAndName(part.getText());
      if (optionTextAndName != null) {
        final String optionText = optionTextAndName.first;
        final String optionName = optionTextAndName.second;
        final WordWithPosition option = new WordWithPosition(optionText, part.getFrom(), part.getFrom() + optionText.length());

        final WordWithPosition optionArgument;
        if (optionText.length() == part.getText().length()) {
          optionArgument = null;
        }
        else {
          // Looks like we have option argument here.
          final String argumentText = part.getText().substring(optionText.length());
          optionArgument = new WordWithPosition(argumentText, option.getTo(), part.getTo());
        }
        optionsAndArguments.add(new CommandLineOption(option, optionName, optionArgument)); //Option
        if (optionArgument != null) {
          optionsAndArguments.add(new CommandLineArgument(optionArgument)); // And its argument
        }
      }
      else {
        optionsAndArguments.add(new CommandLineArgument(part)); //Not an option. Should be argument
      }
    }
    return new CommandLine(command, optionsAndArguments);
  }


  /**
   * Parse out option and its value iterating through the parsers
   *
   * @param text text believed to be an option like "--foo=123"
   * @return [option_text, option_name] or null of no such option
   * @see OptionParser#findOptionTextAndName(String)
   */
  @Nullable
  private static Pair<String, String> findOptionTextAndName(@NotNull final String optionText) {
    for (final OptionParser parser : OPTION_PARSERS) {
      final Pair<String, String> optionTextAndName = parser.findOptionTextAndName(optionText);
      if (optionTextAndName != null) {
        return optionTextAndName;
      }
    }
    return null;
  }
}
