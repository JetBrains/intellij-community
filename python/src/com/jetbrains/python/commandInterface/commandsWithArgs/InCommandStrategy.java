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

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Strategy implementation for case when user entered command
 *
 * @author Ilya.Kazakevich
 */
class InCommandStrategy extends Strategy {
  @NotNull
  private final List<String> myArguments = new ArrayList<String>();
  @NotNull
  private final Command myCommand;

  /**
   * @param command   command enrtered by user
   * @param presenter presenter
   */
  InCommandStrategy(@NotNull final Command command, @NotNull final CommandInterfacePresenterCommandBased presenter) {
    super(presenter);
    final List<String> parts = Arrays.asList(presenter.getTextAsParts());
    assert !parts.isEmpty() && parts.get(0).equals(command.getName()) : "At least first argument should be command";
    myArguments.addAll(parts.subList(1, parts.size()));
    myCommand = command;
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

  @Override
  boolean isUnknownTextExists() {
    if (myPresenter.getTextAsParts().length == 1) {
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
