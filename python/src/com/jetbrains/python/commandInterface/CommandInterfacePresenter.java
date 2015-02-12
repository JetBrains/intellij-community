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

import com.jetbrains.python.vp.Presenter;
import org.jetbrains.annotations.Nullable;

/**
 * Presenter for command-line interface to be paired with view.
 *
 * @author Ilya.Kazakevich
 */
public interface CommandInterfacePresenter extends Presenter {
  /**
   * Called by view when user types new text or text changed by some other reason
   *
   */
  void textChanged();

  /**
   * Called by view when user requests for completion (like tab)
   *
   * @param valueFromSuggestionList value selected from suggestion list (if any selected)
   */
  void completionRequested(@Nullable String valueFromSuggestionList);

  /**
   * Called by view when user asks for suggestions (like CTRL+Space)
   */
  void suggestionRequested();

  /**
   * Called by view when user wants to execute command (like enter)
   *
   */
  void executionRequested();
}
