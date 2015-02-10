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

import com.intellij.openapi.keymap.KeymapUtil;
import com.jetbrains.python.commandInterface.CommandInterfacePresenter;
import com.jetbrains.python.suggestionList.SuggestionList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Key strokes to be used with view.
 * Strokes paired with action. You need to register each action via {@link javax.swing.InputMap}
 *
 * @author Ilya.Kazakevich
 */
enum KeyStrokeInfo {
  /**
   * "Execute command" keystroke
   */
  EXECUTION(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)),
  /**
   * "Complete current command or argument" keystroke
   */
  COMPLETION(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)),
  /**
   * "Display suggestions" keystroke.
   */
  SUGGESTION(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK)),;

  /**
   * List of actions. Each action should be bound to some {@link com.jetbrains.python.commandInterface.swingView.KeyStrokeInfo}
   */
  @NotNull
  private static final KeyStrokeAction[] ACTIONS = {
    new CompletionKeyStrokeAction(),
    new ExecutionKeyStrokeAction(),
    new SuggestionKeyStrokeAction()};

  private final KeyStroke myStroke;

  KeyStrokeInfo(@NotNull final KeyStroke stroke) {
    myStroke = stroke;
  }

  /**
   * Registers  action and binds it appropriate stroke. Call if for all instances to make all actions available.
   *
   * @param presenter      presenter to be used as call back
   * @param suggestionList suggestion list to be used as call back
   * @param source         Component with {@link javax.swing.InputMap} and {@link javax.swing.ActionMap} (swing view itself)
   */
  void register(@NotNull final CommandInterfacePresenter presenter,
                @NotNull final SuggestionList suggestionList,
                @NotNull final JComponent source) {
    final KeyStrokeAction action = getAction();
    final String strokeName = action.configure(presenter, suggestionList);
    source.getInputMap().put(myStroke, strokeName);
    source.getActionMap().put(strokeName, action);
  }

  /**
   * @return Human-readable name of this action (to display it to user)
   */
  @NotNull
  String getText() {
    return KeymapUtil.getKeystrokeText(myStroke);
  }


  /**
   * @return action paired with stroke
   */
  @NotNull
  private KeyStrokeAction getAction() {
    for (final KeyStrokeAction action : ACTIONS) {
      if (action.getStroke() == this) {
        return action;
      }
    }
    throw new IllegalStateException("Failed to find action for " + name());
  }
}
