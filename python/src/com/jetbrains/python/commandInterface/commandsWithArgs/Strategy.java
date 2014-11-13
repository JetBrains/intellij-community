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
  protected final CommandInterfacePresenterCommandBased myPresenter;

  /**
   * @param presenter presenter
   */
  protected Strategy(@NotNull final CommandInterfacePresenterCommandBased presenter) {
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


  /**
   * @return command that entered in box, or null of just entered
   */
  @Nullable
  abstract CommandExecutionInfo getCommandToExecute();


  /**
   * @return errors
   */
  @NotNull
  abstract ErrorInfo getShowErrorInfo();

  /**
   * @return if text entered by user contains some unknown commands
   */
  abstract boolean isUnknownTextExists();

  /**
   * Display error or not
   */
  enum ErrorInfo {
    /**
     * Yes, mark whole text as error
     */
    FULL,
    /**
     * Yes, mark last part as error
     */
    RELATIVE,
    /**
     * No, do not mark anything like error
     */
    NO
  }

  @SuppressWarnings("PackageVisibleField") // No need to hide field: everything is internal API in package, anyway
  static class SuggestionInfo {
    /**
     * Suggestions
     */
    private final List<String> mySuggestions = new ArrayList<String>();
    /**
     * Display them at absolute location or relative to last letter
     */
    final boolean myAbsolute;
    /**
     * Show then any time, or only when user requests them
     */
    final boolean myShowOnlyWhenRequested;

    /**
     * @param absolute              Display them at absolute location or relative to last letter
     * @param showOnlyWhenRequested Show then any time, or only when user requests them
     * @param suggestions           Suggestions
     */
    SuggestionInfo(final boolean absolute,
                   final boolean showOnlyWhenRequested,
                   @NotNull final List<String> suggestions) {
      myAbsolute = absolute;
      myShowOnlyWhenRequested = showOnlyWhenRequested;
      mySuggestions.addAll(suggestions);
    }

    /**
     * @return suggestions
     */
    @NotNull
    List<String> getSuggestions() {
      return Collections.unmodifiableList(mySuggestions);
    }
  }
}
