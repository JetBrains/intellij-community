/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.commandInterface.chunkDriverBasedPresenter;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Information about suggestions
 *
 * @author Ilya.Kazakevich
 */
public final class SuggestionInfo {
  @NotNull
  private final List<String> mySuggestions = new ArrayList<String>();
  private final boolean myShowSuggestionsAutomatically;
  private final boolean myShowAbsolute;

  /**
   * @param showSuggestionsAutomatically true it suggestions should be displayed even if user did not ask for that
   * @param showAbsolute                 show suggestions at the absolute position (not relative to caret).
   * @param suggestions                  List of suggestions to display
   */
  public SuggestionInfo(final boolean showSuggestionsAutomatically,
                        final boolean showAbsolute,
                        @NotNull final Collection<String> suggestions) {
    myShowSuggestionsAutomatically = showSuggestionsAutomatically;
    myShowAbsolute = showAbsolute;
    mySuggestions.addAll(suggestions);
  }

  /**
   * @return List of suggestions to display
   */
  @NotNull
  public List<String> getSuggestions() {
    return Collections.unmodifiableList(mySuggestions);
  }

  /**
   * @return true it suggestions should be displayed even if user did not ask for that
   */
  public boolean isShowSuggestionsAutomatically() {
    return myShowSuggestionsAutomatically;
  }

  /**
   * @return show suggestions at the absolute position (not relative to caret).
   */
  public boolean isShowAbsolute() {
    return myShowAbsolute;
  }
}
