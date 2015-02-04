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
package com.jetbrains.python.optParse;

import org.jetbrains.annotations.NotNull;

import java.util.*;

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
public final class ParsedCommandLine {
  @NotNull
  private final WordWithPosition myCommand;
  @NotNull
  private final List<WordWithPosition> myArguments = new ArrayList<WordWithPosition>();

  /**
   * @param commandLine command line to parse
   * @throws MalformedCommandLineException if malformed commandline can't be parsed
   */
  public ParsedCommandLine(@NotNull final String commandLine) throws MalformedCommandLineException {
    final Deque<WordWithPosition> parts = new LinkedList<WordWithPosition>(WordWithPosition.splitText(commandLine));
    if (parts.isEmpty()) {
      throw new MalformedCommandLineException("No command provided");
    }
    myCommand = parts.pop();
    if (myCommand.getText().startsWith("-")) {
      throw new MalformedCommandLineException("Command can't start with option prefix");
    }

    // TODO: Support option arguments!!! Not only bool options exist! Check nargs!
    for (final WordWithPosition part : parts) {
      if (part.getText().startsWith("-")) {
        // This is option!
      }
      else {
        // TODO: Check optopn argument!
        myArguments.add(part);
      }
    }
  }


  /**
   * @return command (i.e. "startapp" in "startapp my_app")
   */
  @NotNull
  public WordWithPosition getCommand() {
    return myCommand;
  }

  /**
   * @return all arguments (not options or option arguments!)
   */
  @NotNull
  public List<WordWithPosition> getArguments() {
    return Collections.unmodifiableList(myArguments);
  }

  /**
   * @return all parts for command line as simple words
   */
  @NotNull
  public List<String> getAsWords() {
    final List<String> result = new ArrayList<String>();

    result.add(myCommand.getText());

    for (final WordWithPosition argument : myArguments) {
      result.add(argument.getText());
    }
    // TODO: Add options as well
    return result;
  }

  // TODO: Add options, arguments and option arguments
}
