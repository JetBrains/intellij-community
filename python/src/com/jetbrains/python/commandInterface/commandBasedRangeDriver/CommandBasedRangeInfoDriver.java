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
package com.jetbrains.python.commandInterface.commandBasedRangeDriver;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Range;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.commandInterface.command.Argument;
import com.jetbrains.python.commandInterface.command.Command;
import com.jetbrains.python.commandInterface.command.Option;
import com.jetbrains.python.commandInterface.command.OptionArgumentInfo;
import com.jetbrains.python.commandInterface.rangeBasedPresenter.Executor;
import com.jetbrains.python.commandInterface.rangeBasedPresenter.RangeInfo;
import com.jetbrains.python.commandInterface.rangeBasedPresenter.RangeInfoDriver;
import com.jetbrains.python.commandInterface.rangeBasedPresenter.SuggestionInfo;
import com.jetbrains.python.commandLineParser.CommandLine;
import com.jetbrains.python.commandLineParser.CommandLineParser;
import com.jetbrains.python.commandLineParser.CommandLinePart;
import com.jetbrains.python.commandLineParser.MalformedCommandLineException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Driver that returns pack of range infos for certain command line
 *
 * @author Ilya.Kazakevich
 */
public final class CommandBasedRangeInfoDriver implements RangeInfoDriver {
  @NotNull
  private final Map<String, Command> myCommands = new TreeMap<String, Command>(); // To sort commands by name
  @NotNull
  private final Module myModule;
  @NotNull
  private final CommandLineParser myCommandLineParser;

  /**
   *
   * @param module module (to be used in execution)
   * @param commandLineParser parser to parse command lines
   * @param commands available commands
   */
  public CommandBasedRangeInfoDriver(
    @NotNull final Module module,
    @NotNull final CommandLineParser commandLineParser,
    @NotNull final Collection<? extends Command> commands) {
    for (final Command command : commands) {
      myCommands.put(command.getName(), command);
    }
    myModule = module;
    myCommandLineParser = commandLineParser;
  }

  @NotNull
  @Override
  public Pair<Executor, List<RangeInfo>> getCommandLineInfo(@NotNull final String commandLineText) {
    // TODO: Copty/paste with exceptiojn
    if (StringUtil.isEmpty(commandLineText)) {
      final RangeInfo info = new RangeInfo(null, "", new SuggestionInfo(
        true, true, myCommands.keySet()
      ), RangeInfo.TERMINATION_RANGE, false
      );
      return new Pair<Executor, List<RangeInfo>>(null, Collections.singletonList(info));
    }
    final CommandLine commandLine;
    try {
      commandLine = myCommandLineParser.parse(commandLineText);
    }
    catch (final MalformedCommandLineException ignored) {
      return new Pair<Executor, List<RangeInfo>>(null, Collections.singletonList(
        new RangeInfo(null, PyBundle.message("commandLine.validation.badCommand"), new SuggestionInfo(
          false, true, myCommands.keySet()
        ), new Range<Integer>(0, commandLineText.length()), false)
      ));
    }

    final Command command = getExistingCommand(commandLine);
    final List<CommandLinePart> commandLineParts = commandLine.getParts();
    if (command == null) {
      // Bad command inserted
      return createBadCommandInfo(commandLine);
    }

    final UnusedOptionsCollector unusedOptionsCollector = UnusedOptionsCollector.create(command, commandLineParts);
    final RangeInfoCollector rangeInfoCollector = RangeInfoCollector.create(command, commandLineParts, unusedOptionsCollector);



    final SuggestionInfo commandSuggestions = new SuggestionInfo(false, true, myCommands.keySet());
    // Add command as first range info
    final List<RangeInfo> rangeInfos =
      new ArrayList<RangeInfo>(Collections.singletonList(new RangeInfo(null, null, commandSuggestions, commandLine.getCommand(), false)));
    // Then add collected infos
    rangeInfos.addAll(rangeInfoCollector.getRangeInfos());


    /////// What about "after the caret" ?
    final Pair<Boolean, Argument> unsatisfiedArgument = rangeInfoCollector.getUnsatisfiedPositionalArgument();
    final OptionArgumentInfo unsatisfiedOptionArgument = rangeInfoCollector.getUnsatisfiedOptionArgument();


    // TODO: Move to collector after test
    if (unsatisfiedOptionArgument != null) {
      final List<String> availableValues = unsatisfiedOptionArgument.getAvailableValues();
      final SuggestionInfo suggestionInfo;
      if (availableValues != null) {

        suggestionInfo =
          new SuggestionInfo(false, false, availableValues);
      }
      else {
        suggestionInfo = null;
      }
      rangeInfos
        .add(new RangeInfo(null, PyBundle.message("commandLine.validation.optArgMissing"), suggestionInfo, RangeInfo.TERMINATION_RANGE,
                           false));
    }
    else if (unsatisfiedArgument != null) {
      final boolean required = unsatisfiedArgument.first;
      final Argument argument = unsatisfiedArgument.second;
      // Only add error if required
      final String error = required ? PyBundle.message("commandLine.validation.argMissing") : null;
      final List<String> availableValues = unusedOptionsCollector.addUnusedOptions(argument.getAvailableValues());
      final RangeInfo lastArgInfo =
        new RangeInfo(argument.getHelpText(), error,
                      (availableValues != null ? new SuggestionInfo(false, false, availableValues) : null), RangeInfo.TERMINATION_RANGE,
                      false);
      rangeInfos.add(lastArgInfo);
    }
    else {
      // Looks like all arguments are satisfied. Adding empty chunk to prevent completion etc.
      // This is a hack, but with out of it last range info will always be used, even 200 chars after last place
      rangeInfos.add(new RangeInfo(null, null, rangeInfoCollector.getCurrentSuggestions(false, null), RangeInfo.TERMINATION_RANGE, false));
    }

    assert rangeInfos.size() >= commandLineParts.size() : "Contract broken: not enough chunks";
    return Pair.<Executor, List<RangeInfo>>create(new CommandExecutor(command, myModule, commandLine.getPartsAsText()), rangeInfos);
  }

  @Nullable
  private Command getExistingCommand(@NotNull final CommandLine commandLine) {
    return myCommands.get(commandLine.getCommand().getText());
  }


  /**
   * Creates range info info signaling command is bad or junk
   *
   *
   * @param commandLine command line passed by user
   * @return  info to return
   */
  @NotNull
  private Pair<Executor, List<RangeInfo>> createBadCommandInfo(final CommandLine commandLine) {
    final List<RangeInfo> result = new ArrayList<RangeInfo>();
    // We know that first chunk command line, but we can't say anything about outher chunks except they are bad.
    // How ever, we must say something
    result
      .add(new RangeInfo(null, PyBundle.message("commandLine.validation.badCommand"), new SuggestionInfo(true, true, myCommands.keySet()),
                         commandLine.getCommand(), false));
    // Command is unknown, all other parts are junk
    for (final CommandLinePart part : commandLine.getParts()) {
      result.add(new RangeInfo(null, "", false, part.getWord()));
    }


    return Pair.create(null, result);
  }


  /**
   * Finds option by its name
   * @param command current command
   * @param optionName option name
   * @return option or null if no option found
   */
  @Nullable
  static Option findOptionByName(@NotNull final Command command, @NotNull final String optionName) {
    for (final Option option : command.getOptions()) {
      for (final String name : option.getAllNames()) {
        if (name.equals(optionName)) {
          return option;
        }
      }
    }
    return null;
  }
}
