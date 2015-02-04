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
import com.jetbrains.python.commandInterface.CommandInterfaceView.SpecialErrorPlace;
import com.jetbrains.python.optParse.WordWithPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Strategy that helps {@link com.jetbrains.python.commandInterface.commandsWithArgs.CommandInterfacePresenterCommandBased}
 * to deal with 2 states: when no text entered (or some junk enetered) or command name entered
 *
 * @author Ilya.Kazakevich
 */
abstract class Strategy {
  @NotNull
  protected final CommandInterfacePresenterCommandBased<?> myPresenter;

  /**
   * @param presenter presenter
   */
  protected Strategy(@NotNull final CommandInterfacePresenterCommandBased<?> presenter) {
    myPresenter = presenter;
  }

  /**
   * @return sub text to display
   */
  @NotNull
  abstract String getSubText();

  /**
   * @return suggestions
   */
  @NotNull
  abstract SuggestionInfo getSuggestionInfo();

  // TODO: Merge baloon and error (actually the same)
  @NotNull
  List<WordWithPosition> getBalloonsToShow() {
    return Collections.emptyList();
  }

  /**
   * @return command that entered in box, or null of just entered
   */
  @Nullable
  abstract CommandExecutionInfo getCommandToExecute();


  /**
   * @return errors
   */
  @NotNull
  abstract Pair<SpecialErrorPlace, List<WordWithPosition>> getErrorInfo();

  /**
   * @return if text entered by user contains some unknown commands
   */
  abstract boolean isUnknownTextExists();
}
