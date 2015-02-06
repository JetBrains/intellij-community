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
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.commandInterface.CommandInterfaceView.SpecialErrorPlace;
import com.jetbrains.python.optParse.WordWithPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Strategy implementation for case when no command parsed
 *
 * @author Ilya.Kazakevich
 */
final class NoCommandStrategy extends Strategy {

  private static final Pair<SpecialErrorPlace, List<WordWithPosition>>
    NO_ERROR = Pair.create(null, Collections.<WordWithPosition>emptyList());

  NoCommandStrategy(@NotNull final CommandInterfacePresenterCommandBased<?> presenter) {
    super(presenter);
  }

  @NotNull
  @Override
  String getSubText() {
    return PyBundle.message("commandsWithArgs.enterCommand.label");
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
  Pair<SpecialErrorPlace, List<WordWithPosition>> getErrorInfo() {
    // No error if textbox empty, but mark everything as error if some text entered: it is junk (it can't be command,
    // InCommand strategy were selected otherwise)
    return isTextBoxEmpty() ? NO_ERROR : Pair.create(SpecialErrorPlace.WHOLE_TEXT, Collections.<WordWithPosition>emptyList());
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
