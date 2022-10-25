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
  public static final char NEWLINE = '\u32E1';

  public ReplaceTextCommand(@NotNull String command, int line) {
    super(command, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    final AsyncPromise<Object> result = new AsyncPromise<>();

    String text = getText().substring(PREFIX.length() + 1).replace(NEWLINE, '\n');

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
          WriteCommandAction.runWriteCommandAction(context.getProject(),
                                                   () -> document.replaceText(text, document.getModificationStamp() + 1));
          result.setResult(null);
        } else {
          result.setError("Cannot replace text on non-Editor component");
        }
      });
    });

    return result;
  }
}
