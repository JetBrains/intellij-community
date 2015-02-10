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

import com.intellij.util.Range;
import com.jetbrains.python.WordWithPosition;
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
   * Special place in command line that represents "after the last character" place.
   * To be used in methods like {@link #setInfoAndErrors(java.util.Collection, java.util.Collection)} to mark it.
   */
  @NotNull
  Range<Integer> AFTER_LAST_CHARACTER_RANGE = new Range<Integer>(Integer.MAX_VALUE, Integer.MAX_VALUE);

  /**
   * Launches view
   */
  void show();

  /**
   * Suggests user some elements (for completion reason)
   *
   * @param suggestions what to suggest (see {@link com.jetbrains.python.suggestionList.SuggestionsBuilder})
   * @param absolute    display list in its main position, or directly near the caret
   * @param toSelect    word to select in list (if any)
   */
  void displaySuggestions(@NotNull SuggestionsBuilder suggestions, boolean absolute, @Nullable String toSelect);


  /**
   * Each time caret meets certain place, view should check whether some subtext has to be displayed.
   * There are 2 types of subtext to be displayed:
   * <ol>
   * <li>Suggestion Text: View says something like "click FOO to see list of suggestions". Only presenter knows exact places where
   * suggestions are available, so it should provide them</li>
   * <li>Default text: In all other cases view displays default text (if available).</li>
   * </ol>
   * <p/>
   * Presenter provides view list of special places
   *
   * @param defaultSubText            default text
   * @param suggestionAvailablePlaces list of places where suggestions are available in format [from, to].
   */
  void configureSubTexts(@Nullable String defaultSubText,
                         @NotNull List<Range<Integer>> suggestionAvailablePlaces);

  /**
   * Hide suggestion list
   */
  void removeSuggestions();


  /**
   * @return text, entered by user
   */
  @NotNull
  String getText();


  /**
   * When caret meets certain place, view may display some info and some errors.
   * Errors, how ever, may always be emphasized (with something like red line).
   * This function configures view with pack of ranges and texts to display.
   * Special place {@link #AFTER_LAST_CHARACTER_RANGE} may also be used.
   * Each place is described as start-end position (in chars) where it should be enabled.
   * Each call removes previously enabled information.
   *
   * @param errors       places to be marked as errors with error text.
   * @param infoBalloons places to display info balloon
   * @see #AFTER_LAST_CHARACTER_RANGE
   */
  void setInfoAndErrors(@NotNull final Collection<WordWithPosition> infoBalloons, @NotNull final Collection<WordWithPosition> errors);


  /**
   * Inserts text after caret moving next chars to the right
   *
   * @param text text to insert
   */
  void insertTextAfterCaret(@NotNull String text);

  /**
   * Replaces current text with another one.
   *
   * @param from    from
   * @param to      to
   * @param newText text to replace
   */
  void replaceText(final int from, final int to, @NotNull String newText);

  /**
   * @return position of caret (in chars)
   */
  int getCaretPosition();
}
