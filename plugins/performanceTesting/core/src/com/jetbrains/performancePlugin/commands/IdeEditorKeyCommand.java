// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.KeyCodeTypeCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import com.jetbrains.performancePlugin.utils.EditorUtils;
import com.jetbrains.performancePlugin.utils.HighlightingTestUtil;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

/**
 * Command simulates pressing a keyboard key.
 * Only defined set of key is supported for now: "ENTER", "BACKSPACE", "TAB", "ESCAPE", "ARROW_DOWN" and "ARROW_UP"
 * <p>
 * Syntax: %pressKey <KEY>
 * Example: %pressKey ENTER
 */
public class IdeEditorKeyCommand extends KeyCodeTypeCommand {

  public static final String PREFIX = CMD_PREFIX + "pressKey";

  public IdeEditorKeyCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  public @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();

    String input = extractCommandArgument(PREFIX);
    try {
      pressKey(EditorKey.valueOf(input), context.getProject());
      actionCallback.setDone();
    }
    catch (Throwable e) {
      actionCallback.reject(e.getMessage());
    }

    return Promises.toPromise(actionCallback);
  }

  public static void pressKey(EditorKey editorKey, Project project) {
    String actionID = editorKey.action;
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      ApplicationManager.getApplication().runWriteAction(Context.current().wrap(() -> {
        TraceKt.use(PerformanceTestSpan.TRACER.spanBuilder(DelayTypeCommand.SPAN_NAME), span -> {
          AnAction action = ActionManagerEx.getInstanceEx().getAction(actionID);
          AnActionEvent actionEvent = AnActionEvent.createFromAnAction(action, null, "", EditorUtils.createEditorContext(editor));
          ActionManagerEx actionManager = (ActionManagerEx)actionEvent.getActionManager();
          actionManager.performWithActionCallbacks(action, actionEvent, () ->
            CommandProcessor.getInstance().executeCommand(project, () ->
              action.actionPerformed(actionEvent), "", null, editor.getDocument()));
          span.addEvent("Typing " + actionID);
          return null;
        });
      }));

      HighlightingTestUtil.storeProcessFinishedTime(
        "pressKey",
        "typing_target_" + editor.getVirtualFile().getName(),
        Pair.pair("typed_text", actionID));
    }
    else {
      throw new IllegalStateException("Editor is not opened");
    }
  }

  public enum EditorKey {
    ENTER(IdeActions.ACTION_EDITOR_ENTER),
    BACKSPACE(IdeActions.ACTION_EDITOR_BACKSPACE),
    TAB(IdeActions.ACTION_EDITOR_TAB),
    ESCAPE(IdeActions.ACTION_EDITOR_ESCAPE),
    ARROW_DOWN(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN),
    ARROW_UP(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP);

    private final String action;

    EditorKey(String action) {
      this.action = action;
    }
  }
}
