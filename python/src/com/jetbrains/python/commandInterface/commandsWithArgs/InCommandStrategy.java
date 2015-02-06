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

import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.commandInterface.CommandInterfaceView;
import com.jetbrains.python.commandInterface.CommandInterfaceView.SpecialErrorPlace;
import com.jetbrains.python.commandInterface.commandsWithArgs.ArgumentsValuesValidationInfo.ArgumentValueError;
import com.jetbrains.python.optParse.ParsedCommandLine;
import com.jetbrains.python.optParse.WordWithPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Strategy implementation for case when user entered command
 *
 * @author Ilya.Kazakevich
 */
final class InCommandStrategy extends Strategy {
  @NotNull
  private final List<String> myArguments = new ArrayList<String>();
  @NotNull
  private final Command myCommand;
  @NotNull
  private final ParsedCommandLine myCommandLine;

  /**
   * @param command   command enrtered by user
   * @param presenter presenter
   */
  InCommandStrategy(@NotNull final Command command,
                    @NotNull final ParsedCommandLine commandLine,
                    @NotNull final CommandInterfacePresenterCommandBased<?> presenter) {
    super(presenter);
    myArguments.addAll(WordWithPosition.fetchText(commandLine.getArguments()));
    myCommand = command;
    myCommandLine = commandLine;
  }

  @NotNull
  @Override
  public String getSubText() {
    final String help = myCommand.getHelp();
    if (help != null) {
      return help;
    }
    return "Place to display help";
  }

  @NotNull
  @Override
  SuggestionInfo getSuggestionInfo() {
    final Argument nextArgument = myCommand.getArgumentsInfo().getArgument(myCommandLine.getArguments().size());
    if (nextArgument != null) {// TODO: Check options!
      // If next arg exists
      final List<String> availableValues = nextArgument.getAvailableValues();
      if (availableValues != null) { // If has available values
        return new SuggestionInfo(false, false, availableValues);
      }
    }
    return new SuggestionInfo(false, false, Collections.<String>emptyList());
  }

  @NotNull
  @Override
  List<WordWithPosition> getBalloonsToShow() {
    // Display argument balloons right from command end to last argument end
    final ArgumentsInfo argumentsInfo = myCommand.getArgumentsInfo();
    final List<WordWithPosition> arguments = myCommandLine.getArguments();
    if (arguments.isEmpty()) {
      // If no arguments provided, then display first argument popup right after command
      final Argument firstArgument = argumentsInfo.getArgument(0);
      if (firstArgument == null) {
        return Collections.emptyList(); // Looks like no argument required
      }
      return Collections
        .singletonList(new WordWithPosition(firstArgument.getHelpText(), myCommandLine.getCommand().getTo() + 1));
    }

    final List<WordWithPosition> result = new ArrayList<WordWithPosition>(arguments.size());
    for (int i = 0; i < arguments.size(); i++) {
      final WordWithPosition argEnteredByUser = arguments.get(i);
      final Argument argument = argumentsInfo.getArgument(i);
      if (argument != null) {
        // Display argument help
        result.add(argEnteredByUser.copyWithDifferentText(argument.getHelpText()));
      }
    }
    return result;
  }

  @Override
  boolean isUnknownTextExists() {
    if (myCommandLine.getAsWords().isEmpty()) {
      return false; // Command only
    }
    final String lastPart = myPresenter.getLastPart();
    return ((lastPart != null) && !getSuggestionInfo().getSuggestions().contains(lastPart));
  }

  @Nullable
  @Override
  CommandExecutionInfo getCommandToExecute() {
    return new CommandExecutionInfo(myCommand.getName(), ArrayUtil.toStringArray(myArguments));
  }

  @NotNull
  @Override
  Pair<SpecialErrorPlace, List<WordWithPosition>> getErrorInfo() {
    final List<WordWithPosition> userProvidedValues = myCommandLine.getArguments();
    SpecialErrorPlace specialError = null;
    final List<WordWithPosition> errors = new ArrayList<WordWithPosition>();

    final ArgumentsValuesValidationInfo validation =
      myCommand.getArgumentsInfo().validateArgumentValues(WordWithPosition.fetchText(userProvidedValues));
    if (validation.isNotEnoughArguments()) {
      specialError = SpecialErrorPlace.AFTER_LAST_CHAR;
    }
    for (final Entry<Integer, ArgumentValueError> errorEntry : validation.getPositionOfErrorArguments().entrySet()) {
      final String errorText = (errorEntry.getValue() == ArgumentValueError.BAD_VALUE ?
                                PyBundle.message("commandsWithArgs.validation.badValue") :
                                PyBundle.message("commandsWithArgs.validation.excess") );
      errors.add(userProvidedValues.get(errorEntry.getKey()).copyWithDifferentText(errorText));
    }


    return Pair.create(specialError, errors);
  }
}
