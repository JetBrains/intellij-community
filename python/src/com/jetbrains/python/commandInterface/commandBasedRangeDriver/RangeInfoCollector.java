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

import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.WordWithPosition;
import com.jetbrains.python.commandInterface.command.Argument;
import com.jetbrains.python.commandInterface.command.Command;
import com.jetbrains.python.commandInterface.command.Option;
import com.jetbrains.python.commandInterface.command.OptionArgumentInfo;
import com.jetbrains.python.commandInterface.rangeBasedPresenter.RangeInfo;
import com.jetbrains.python.commandInterface.rangeBasedPresenter.SuggestionInfo;
import com.jetbrains.python.commandLineParser.CommandLineArgument;
import com.jetbrains.python.commandLineParser.CommandLineOption;
import com.jetbrains.python.commandLineParser.CommandLinePart;
import com.jetbrains.python.commandLineParser.CommandLinePartVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Visitor that collections ranges info by visiting options and arguments
 *
 * @author Ilya.Kazakevich
 */
final class RangeInfoCollector implements CommandLinePartVisitor {
  /**
   * Options already met in command line. It has nothing to do with {@link UnusedOptionsCollector}!
   */
  @NotNull
  private final Set<String> myUsedOptions = new HashSet<String>();
  @NotNull
  private final List<RangeInfo> myRangeInfos = new ArrayList<RangeInfo>();
  @NotNull
  private final Command myCommand;

  private int myNumOfProcessedPositionalArguments = 0;
  private boolean mySkipNextArgument;
  private Pair<Integer, OptionArgumentInfo> myExpectedOptionArgument;
  @NotNull
  private final UnusedOptionsCollector myUnusedOptionsCollector;

  /**
   * @param command                command
   * @param unusedOptionsCollector instance of unused options collector
   */
  private RangeInfoCollector(@NotNull final Command command,
                             @NotNull final UnusedOptionsCollector unusedOptionsCollector) {
    myCommand = command;
    myUnusedOptionsCollector = unusedOptionsCollector;
  }

  @NotNull
  static RangeInfoCollector create(@NotNull final Command command,
                                   @NotNull final Iterable<CommandLinePart> commandLineParts,
                                   @NotNull final UnusedOptionsCollector unusedOptionsCollector) {
    final RangeInfoCollector infoCollector = new RangeInfoCollector(command, unusedOptionsCollector);
    for (final CommandLinePart part : commandLineParts) {
      part.accept(infoCollector);
    }
    return infoCollector;
  }

  @Override
  public void visitOption(@NotNull final CommandLineOption option) {
    if (processOptionArgument(option.getWord())) {
      return;
    }


    final Option commandOption = CommandBasedRangeInfoDriver.findOptionByName(myCommand, option.getOptionName());
    final WordWithPosition attachedArgument = option.getAttachedArgument();
    if (commandOption == null) {
      // There is no such option
      // To Mark attached arg so we skip it in {@link #visitArgument}
      if (attachedArgument != null) {
        mySkipNextArgument = true;
      }
      // No such option
      myRangeInfos.add(new RangeInfo(null, PyBundle.message("commandLine.validation.badOption"), getCurrentSuggestions(false, null),
                                     option.getWord(), false));
      return;
    }


    final Pair<Integer, OptionArgumentInfo> argumentAndQuantity = commandOption.getArgumentAndQuantity();


    // If option already used, then mark it
    final String optionAlreadyUsed = Sets.intersection(Sets.newHashSet(commandOption.getAllNames()), myUsedOptions).isEmpty()
                                     ? null
                                     : PyBundle.message("commandLine.validation.badOption");
    myRangeInfos
      .add(new RangeInfo(commandOption.getHelp(), optionAlreadyUsed, getCurrentSuggestions(false, null), option.getWord(),
                         argumentAndQuantity != null));

    // remove from existing options
    myUsedOptions.addAll(commandOption.getAllNames());


    if (argumentAndQuantity == null) {
      if (attachedArgument != null) {
        myRangeInfos.add(new RangeInfo(null, PyBundle.message("commandLine.validation.noArgAllowed"), false, attachedArgument));
        mySkipNextArgument = true;
      }
    }
    else {
      // Some option args required
      myExpectedOptionArgument = argumentAndQuantity;
    }
  }

  /**
   * Process option argument (not to be confused with positional!)
   *
   * @param currentPart part with argument.
   * @return false if there should not be any option argument so this method did nothing. True if there should be and it is processed.
   */
  private boolean processOptionArgument(@NotNull final WordWithPosition currentPart) {
    if (myExpectedOptionArgument == null) {
      return false;
    }
    final OptionArgumentInfo argumentInfo = myExpectedOptionArgument.second;
    int argsLeft = myExpectedOptionArgument.first;

    final boolean valid = argumentInfo.isValid(currentPart.getText());
    final List<String> availableValues = argumentInfo.getAvailableValues();
    final SuggestionInfo suggestions;
    if (availableValues != null) {
      suggestions = new SuggestionInfo(false, false, availableValues);
    }
    else {
      suggestions = null;
    }

    myRangeInfos.add(new RangeInfo(null, (valid ? null : PyBundle.message("commandLine.validation.argBadValue")),
                                   suggestions, currentPart, false));


    if (--argsLeft == 0) {
      myExpectedOptionArgument = null;
    }
    else {
      myExpectedOptionArgument = Pair.create(argsLeft, argumentInfo);
    }
    return true;
  }


  /**
   * Returns suggestions available for current position.
   *
   * @param showAutomatically make suggestions displayed automatically
   * @param argumentPair      argument to use. If null, current argument according to internal counter would be used.
   * @return suggestiom info or null if no suggestion available
   */
  @Nullable
  SuggestionInfo getCurrentSuggestions(final boolean showAutomatically, @Nullable Pair<Boolean, Argument> argumentPair) {
    if (argumentPair == null) {
      argumentPair = myCommand.getArgumentsInfo().getArgument(myNumOfProcessedPositionalArguments);
    }

    final List<String> suggestions = argumentPair != null ? myUnusedOptionsCollector.addUnusedOptions(
      argumentPair.second.getAvailableValues()) : myUnusedOptionsCollector.addUnusedOptions(
      null);
    if (suggestions == null) {
      return null;
    }
    return new SuggestionInfo(showAutomatically, false, suggestions);
  }

  @Override
  public void visitArgument(@NotNull final CommandLineArgument argument) {
    if (mySkipNextArgument) {
      // Skip argument, clear flag and do nothing
      mySkipNextArgument = false;
      return;
    }

    if (processOptionArgument(argument.getWord())) {
      return;
    }

    final Pair<Boolean, Argument> argumentPair = myCommand.getArgumentsInfo().getArgument(myNumOfProcessedPositionalArguments++);
    if (argumentPair == null) {
      //Exceed!
      myRangeInfos.add(new RangeInfo(null, PyBundle.message("commandLine.validation.excessArg"), false, argument.getWord()));
      return;
    }
    final Argument commandArgument = argumentPair.second;

    final List<String> argumentAvailableValues = commandArgument.getAvailableValues();
    final String argumentValue = argument.getWord().getText();
    String errorMessage = null;
    if (argumentAvailableValues != null && !argumentAvailableValues.contains(argumentValue)) {
      // Bad value
      errorMessage = PyBundle.message("commandLine.validation.argBadValue");
    }
    // Argument  seems to be ok. We suggest values automatically only if value is bad
    myRangeInfos.add(new RangeInfo(commandArgument.getHelpText(), errorMessage,
                                   getCurrentSuggestions(errorMessage != null, argumentPair),
                                   argument.getWord(), false));
  }

  /**
   * @return calculates range infos for all command line parts (not the command itself!)
   */
  @NotNull
  Collection<RangeInfo> getRangeInfos() {
    return Collections.unmodifiableList(myRangeInfos);
  }


  /**
   * @return Unsatisfied (currently expected) positional argument ([required, arg]) or null if no arg expected
   */
  @Nullable
  Pair<Boolean, Argument> getUnsatisfiedPositionalArgument() {
    return myCommand.getArgumentsInfo().getArgument(myNumOfProcessedPositionalArguments);
  }

  /**
   * @return Unsatisfied (currently expected) option argument or null if no option argument expected
   */
  public OptionArgumentInfo getUnsatisfiedOptionArgument() {
    if (myExpectedOptionArgument != null) {
      return myExpectedOptionArgument.second;
    }
    else {
      return null;
    }
  }
}
