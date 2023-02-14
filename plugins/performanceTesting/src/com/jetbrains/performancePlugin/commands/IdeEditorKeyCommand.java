package com.jetbrains.performancePlugin.commands;

import com.intellij.diagnostic.telemetry.TraceUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.KeyCodeTypeCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import com.jetbrains.performancePlugin.utils.EditorUtils;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class IdeEditorKeyCommand extends KeyCodeTypeCommand {

  public static final String PREFIX = CMD_PREFIX + "pressKey";
  private String actionID;

  public IdeEditorKeyCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  public @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();

    String input = extractCommandArgument(PREFIX);
    switch (StringUtil.toUpperCase(input)) {
      case "ENTER" -> actionID = IdeActions.ACTION_EDITOR_ENTER;
      case "BACKSPACE" -> actionID = IdeActions.ACTION_EDITOR_BACKSPACE;
      case "TAB" -> actionID = IdeActions.ACTION_EDITOR_TAB;
      case "ESCAPE" -> actionID = IdeActions.ACTION_EDITOR_ESCAPE;
      default -> {
        actionCallback.reject("Unknown special character. Please use: ENTER, BACKSPACE, ESCAPE or TAB");
        return Promises.toPromise(actionCallback);
      }
    }

    @Nullable Project project = context.getProject();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      ApplicationManager.getApplication().runWriteAction(Context.current().wrap(() -> {
        TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER, DelayTypeCommand.SPAN_NAME, span -> {
          AnAction action = ActionManagerEx.getInstanceEx().getAction(actionID);
          AnActionEvent actionEvent = AnActionEvent.createFromAnAction(action, null, "", EditorUtils.createEditorContext(editor));
          ActionUtil.performDumbAwareWithCallbacks(action, actionEvent, () -> {
            CommandProcessor.getInstance().executeCommand(project, () -> {
              action.actionPerformed(actionEvent);
            }, "", null, editor.getDocument());
          });
          span.addEvent("Typing " + actionID);
        });
        actionCallback.setDone();
      }));
    }
    else {
      actionCallback.reject("Editor is not opened");
    }
    return Promises.toPromise(actionCallback);
  }
}
