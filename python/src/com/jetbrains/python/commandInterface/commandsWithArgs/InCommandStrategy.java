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
import com.jetbrains.python.optParse.ParsedCommandLine;
import com.jetbrains.python.optParse.WordWithPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    final List<String> strings = new ArrayList<String>();
    for (final Argument argument : myCommand.getArguments()) {
      if (!myArguments.contains(argument.getName())) {
        strings.add(argument.getName());
      }
    }
    return new SuggestionInfo(false, false, strings);
  }

  @NotNull
  @Override
  List<WordWithPosition> getBalloonsToShow() {
    // Display argument balloons right from command end to last argument end
    final String argumentHelp = myCommand.getArgumentHelp();
    if (StringUtil.isEmpty(argumentHelp)) {
      return Collections.emptyList();
    }
    final List<WordWithPosition> arguments = myCommandLine.getArguments();
    if (arguments.isEmpty()) {
      // If no arguments provided, then display popup right after command
      return Collections.singletonList(new WordWithPosition(argumentHelp, myCommandLine.getCommand().getTo() + 1, Integer.MAX_VALUE));
    }
    final List<WordWithPosition> result = new ArrayList<WordWithPosition>(arguments.size());
    for (final WordWithPosition argument : arguments) {
      result.add(argument.copyWithDifferentText(argumentHelp));
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
  ErrorInfo getShowErrorInfo() {
    final boolean noArgsLeft = getSuggestionInfo().getSuggestions().isEmpty();
    return (noArgsLeft ? ErrorInfo.NO : ErrorInfo.RELATIVE);
  }
}
