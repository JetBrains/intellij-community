// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Puts the caret to the location defined by parameters.
 * <p>
 * Syntax: %goto <line> <column>
 * Example: %goto 25 17
 * Syntax: %goto <offset>
 * Example: %goto 25
 */
public class GoToCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "goto";

  public GoToCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    String input = extractCommandArgument(PREFIX);
    String[] args = input.split(" ");

    if (args.length == 1) {
      int offset = Integer.parseInt(args[0]);
      ApplicationManager.getApplication().invokeLater(() -> {
        Project project = context.getProject();
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        assert editor != null;
        goToOffset(context, actionCallback, editor, offset);
      });
    } else {
      final int line = Integer.parseInt(args[0]) - 1;
      final int columnArg = Integer.parseInt(args[1]);
      final int column = columnArg == 0 ? 0 : columnArg - 1;

      ApplicationManager.getApplication().invokeLater(() -> {
        Project project = context.getProject();
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        assert editor != null;
        Document document = editor.getDocument();
        if (line <= document.getLineCount()) {
          int lineStartOffset = document.getLineStartOffset(line);
          int lineLength = document.getLineEndOffset(line) - lineStartOffset;
          if (column > lineLength) {
            //noinspection TestOnlyProblems
            WriteCommandAction.runWriteCommandAction(project, () ->
              document.insertString(lineStartOffset + lineLength,
                                    IntStream.range(0, column - lineLength).mapToObj(i -> " ").collect(Collectors.joining())));
          }
          int offset = lineStartOffset + column;
          goToOffset(context, actionCallback, editor, offset);
        }
        else {
          context.error("Line is out of range", getLine());
          actionCallback.setRejected();
        }
      }, ModalityState.nonModal());
    }
    return Promises.toPromise(actionCallback);
  }

  private void goToOffset(@NotNull PlaybackContext context, @NotNull ActionCallback actionCallback, @NotNull Editor editor, int offset) {
    final CaretListener caretListener = new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        context.message(PerformanceTestingBundle.message("command.goto.finish"), getLine());
        actionCallback.setDone();
      }
    };
    editor.getCaretModel().addCaretListener(caretListener);
    actionCallback.doWhenDone(() -> editor.getCaretModel().removeCaretListener(caretListener));
    if (editor.getCaretModel().getOffset() == offset) {
      context.message(PerformanceTestingBundle.message("command.goto.finish"), getLine());
      actionCallback.setDone();
    }
    else {
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }
}
