// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.ui.playback.commands.AlphaNumericTypeCommand.findTarget;

public class EvaluateExpressionCompletionCommand extends CompletionCommand {
  public static final String NAME = "doCompleteInEvaluateExpression";
  public static final String PREFIX = CMD_PREFIX + NAME;
  public EvaluateExpressionCompletionCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  public Editor getEditor(PlaybackContext context) {
    TypingTarget typingTarget =  findTarget(context);
    if (typingTarget == null) throw new IllegalStateException("typingTarget is null");
    if (!(typingTarget instanceof EditorComponentImpl)) throw new IllegalStateException("typingTarget is not EditorComponentImpl, but " + typingTarget.getClass());
    return ((EditorComponentImpl) typingTarget).getEditor();
  }
}
