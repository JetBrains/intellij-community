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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Strategy implementation for case when no command parsed
 *
 * @author Ilya.Kazakevich
 */
class NoCommandStrategy extends Strategy {
  NoCommandStrategy(@NotNull final CommandInterfacePresenterCommandBased presenter) {
    super(presenter);
  }

  @NotNull
  @Override
  String getSubText() {
    return "Enter command here"; // TODO: Use u18n
  }

  @NotNull
  @Override
  SuggestionInfo getSuggestionInfo() {
    return new SuggestionInfo(true, isTextBoxEmpty(), new ArrayList<String>(myPresenter.getCommands().keySet()));
  }

  @Override
  boolean isUnknownTextExists() {
    return !myPresenter.getView().getText().isEmpty();
  }

  @NotNull
  @Override
  ErrorInfo getShowErrorInfo() {
    return (isTextBoxEmpty() ? ErrorInfo.NO : ErrorInfo.FULL);
  }


  private boolean isTextBoxEmpty() {
    return myPresenter.getView().getText().isEmpty();
  }

  @Nullable
  @Override
  CommandExecutionInfo getCommandToExecute() {
    return null;
  }
}
