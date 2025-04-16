// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.ui.playback.commands.AlphaNumericTypeCommand.findTarget;

/**
 * Command replace text from startOffset (0 by default) to endOffset (end of document by default) by newText ("" by default)
 * Syntax: %replaceText [-startOffset start] [-endOffset end] [-newText text]
 * Example: %replaceText -startOffset 0 -endOffset 10 -text "/" - replace text from 0 to 10 offset by "/"
 * Example: %replaceText -newText "newText" - replace all text in document by "newText"
 * Example: %replaceText -startOffset 0 -endOffset 50 - replace text form 0 to 50 offset by ""
 */
public class ReplaceTextCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "replaceText";

  public ReplaceTextCommand(@NotNull String command, int line) {
    super(command, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    final AsyncPromise<Object> result = new AsyncPromise<>();
    Options options = new Options();
    Args.parse(options, extractCommandArgument(PREFIX).split(" "), false);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        Waiter.checkCondition(() -> findTarget(context) != null).await(10, TimeUnit.MINUTES);
      }
      catch (InterruptedException e) {
        result.setError(e);
        return;
      }
      ApplicationManager.getApplication().invokeAndWait(() -> {
        TypingTarget target = findTarget(context);
        if (target instanceof EditorComponentImpl) {
          DocumentEx document = ((EditorComponentImpl)target).getEditor().getDocument();
          int startOffset = options.startOffset == null ? 0 : options.startOffset;
          int endOffset = options.endOffset == null ? document.getTextLength() : options.endOffset;
          WriteCommandAction.runWriteCommandAction(context.getProject(),
                                                   () -> document.replaceString(startOffset, endOffset, options.newText));
          result.setResult(null);
        }
        else {
          result.setError("Cannot replace text on non-Editor component");
        }
      });
    });

    return result;
  }

  public static class Options {
    @Argument
    public Integer startOffset;

    @Argument
    public Integer endOffset;

    @Argument
    public String newText = "";
  }
}
