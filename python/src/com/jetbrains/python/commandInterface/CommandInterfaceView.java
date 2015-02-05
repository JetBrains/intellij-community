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

import com.jetbrains.python.optParse.WordWithPosition;
import com.jetbrains.python.suggestionList.SuggestionsBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

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
   * Emphasize errors (like red line and special message).
   *
   * @param errors            list of errors (coordinates and error message. Message may be empty not to display any text)
   * @param specialErrorPlace if you want to underline special place, you may provide it here
   */
  void showErrors(@NotNull final List<WordWithPosition> errors, @Nullable SpecialErrorPlace specialErrorPlace);

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

  /**
   * Displays help balloon when cursor meets certain place.
   * Each balloon is described as start-end position (in chars) where it should be enabled
   * and test to display.
   * <strong>Caution: Each call removes previuos balloons!</strong>
   *
   * @param balloons list of balloons to display (i.e. you want to text 'foo' be displayed when user sets cursor on position
   *                 from 1 to 3, so you add 'foo',1,4 here)
   */
  void setBalloons(@NotNull final Collection<WordWithPosition> balloons);

  /**
   * @return true if current caret position is on the word (no on whitespace)
   */
  boolean isCaretOnWord();


  /**
   * Special place that may be underlined
   */
  enum SpecialErrorPlace {
    /**
     * Whole text (from start to end)
     */
    WHOLE_TEXT,
    /**
     * Only after last character
     */
    AFTER_LAST_CHAR
  }
}
