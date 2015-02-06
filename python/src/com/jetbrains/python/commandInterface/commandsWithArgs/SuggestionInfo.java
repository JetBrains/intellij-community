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
package com.jetbrains.python.commandInterface.commandsWithArgs;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Information abouyt suggestion, provided by {@link com.jetbrains.python.commandInterface.commandsWithArgs.Strategy}
 *
 * @author Ilya.Kazakevich
 */
@SuppressWarnings("PackageVisibleField")
// No do not need to hide field: everything is internal API in package, anyway
final class SuggestionInfo {
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
