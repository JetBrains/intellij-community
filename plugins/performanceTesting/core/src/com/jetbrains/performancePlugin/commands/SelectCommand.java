// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.ui.playback.commands.AlphaNumericTypeCommand.findTarget;

public class SelectCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "selectText";

  public SelectCommand(@NotNull String command, int line) {
    super(command, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    ActionCallback actionCallback = new ActionCallbackProfilerStopper();

    String input = extractCommandArgument(PREFIX);
    String[] lineAndColumn = input.split(" ");
    final int startLine = Integer.parseInt(lineAndColumn[0]) - 1;
    final int startColumn = Integer.parseInt(lineAndColumn[1]) - 1;
    final int endLine = Integer.parseInt(lineAndColumn[2]) - 1;
    final int endColumn = Integer.parseInt(lineAndColumn[3]) - 1;

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        Waiter.checkCondition(() -> findTarget(context) != null).await(10, TimeUnit.MINUTES);
      }
      catch (InterruptedException e) {
        actionCallback.reject(e.getMessage());
        return;
      }

      ApplicationManager.getApplication().invokeAndWait(() -> {
        TypingTarget target = findTarget(context);
        if (target instanceof EditorComponentImpl) {
          EditorImpl editor = ((EditorComponentImpl)target).getEditor();
          Document document = editor.getDocument();
          editor.getSelectionModel()
            .setSelection(document.getLineStartOffset(startLine) + startColumn, document.getLineStartOffset(endLine) + endColumn);
          actionCallback.setDone();
        }
        else {
          actionCallback.reject("Cannot replace text on non-Editor component");
        }
      });
    });

    return Promises.toPromise(actionCallback);
  }
}
