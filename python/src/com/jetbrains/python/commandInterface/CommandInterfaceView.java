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
package com.jetbrains.python.commandInterface;

import com.jetbrains.python.suggestionList.SuggestionsBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * View for command-line interface to be paired with view.
 *
 * @author Ilya.Kazakevich
 */
public interface CommandInterfaceView {

  /**
   * Launches view
   */
  void show();

  /**
   * Suggests user some elements (for completion reason)
   *
   * @param suggestions what to suggest (see {@link com.jetbrains.python.suggestionList.SuggestionsBuilder})
   * @param absolute    display list in its main position, or directly near the text
   * @param toSelect    word to select if list (if any)
   */
  void displaySuggestions(@NotNull SuggestionsBuilder suggestions, boolean absolute, @Nullable String toSelect);

  /**
   * Displays error (like red line)
   *
   * @param lastOnly underline only last letter
   */
  void showError(boolean lastOnly);

  /**
   * Change text to the one provided
   *
   * @param newText text to display
   */
  void forceText(@NotNull String newText);

  /**
   * Display text in sub part (like hint)
   *
   * @param subText text to display
   */
  void setSubText(@NotNull String subText);

  /**
   * Hide suggestion list
   */
  void removeSuggestions();

  /**
   * Displays baloon with message right under the last letter.
   *
   * @param message text to display
   */
  void displayInfoBaloon(@NotNull String message);

  /**
   * @return text, entered by user
   */
  @NotNull
  String getText();

  /**
   * Enlarges view to make it as big as required to display appropriate number of chars
   *
   * @param widthInChars number of chars
   */
  void setPreferredWidthInChars(int widthInChars);
}
