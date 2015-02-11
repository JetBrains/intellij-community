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
package com.jetbrains.python.commandInterface.swingView;

import com.jetbrains.python.commandInterface.CommandInterfacePresenter;
import com.jetbrains.python.suggestionList.SuggestionList;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.TextAction;

/**
 * Action that should be taken for certain {@link javax.swing.KeyStroke} (wrapped in {@link com.jetbrains.python.commandInterface.swingView.KeyStrokeInfo}).
 * You need to call {@link #configure(com.jetbrains.python.commandInterface.CommandInterfacePresenter, com.jetbrains.python.suggestionList.SuggestionList)}
 * to enable one.
 *
 * @author Ilya.Kazakevich
 */
@SuppressWarnings({"InstanceVariableMayNotBeInitialized", "NonSerializableFieldInSerializableClass"}) // Will never serialize
abstract class KeyStrokeAction extends TextAction {
  @NotNull
  private final String myName;
  @NotNull
  private final KeyStrokeInfo myStroke;

  protected CommandInterfacePresenter myPresenter;
  protected SuggestionList mySuggestionList;

  /**
   * @param stroke key stroke to bind this info to
   */
  KeyStrokeAction(@NotNull final KeyStrokeInfo stroke) {
    super(stroke.name());
    myName = stroke.name();
    myStroke = stroke;
  }


  /**
   * Configures action.
   *
   * @param presenter      presenter to be used for call back.
   * @param suggestionList list of suggestions to be used for call back
   * @return name of this action to add to {@link javax.swing.InputMap}
   */
  @NotNull
  final String configure(@NotNull final CommandInterfacePresenter presenter, @NotNull final SuggestionList suggestionList) {
    myPresenter = presenter;
    mySuggestionList = suggestionList;
    return myName;
  }

  /**
   * @return stroke bound to this action
   */
  @NotNull
  final KeyStrokeInfo getStroke() {
    return myStroke;
  }
}
