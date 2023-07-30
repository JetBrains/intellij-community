package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.ui.playback.commands.AlphaNumericTypeCommand.findTarget;

public class ReplaceTextCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "replaceText";
  private static final String DEFAULT_POSITION_SYMBOL = "/";

  public ReplaceTextCommand(@NotNull String command, int line) {
    super(command, line);
  }

  private static String getArgValue(String arg) {
    String[] splitArg = arg.split("=");
    return splitArg.length == 2 ? splitArg[1] : "";
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    final AsyncPromise<Object> result = new AsyncPromise<>();
    String[] args = extractCommandArgument(PREFIX).split(" ");
    String potentialStart = getArgValue(args[0]);
    String potentialEnd = getArgValue(args[1]);
    String text = getArgValue(args[2]);


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
          int startPosition = potentialStart.equals(DEFAULT_POSITION_SYMBOL) ? 0 : Integer.parseInt(potentialStart);
          int endPosition = potentialEnd.equals(DEFAULT_POSITION_SYMBOL) ? document.getTextLength() : Integer.parseInt(potentialEnd);
          WriteCommandAction.runWriteCommandAction(context.getProject(),
                                                   () -> document.replaceString(startPosition, endPosition, text));
          result.setResult(null);
        }
        else {
          result.setError("Cannot replace text on non-Editor component");
        }
      });
    });

    return result;
  }
}
