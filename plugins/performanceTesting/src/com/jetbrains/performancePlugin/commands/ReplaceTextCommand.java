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
 * Command replace text from startPosition (0 by default) to endPosition (end of document by default) by newText ("" by default)
 * Syntax: %replaceText [-startPosition start] [-endPosition end] [-newText text]
 * Example: %replaceText -startPosition 0 -endPosition 10 -text "/" - replace text from 0 to 10 position by "/"
 * Example: %replaceText -newText "newText" - replace all text in document by "newText"
 * Example: %replaceText -startPosition 0 -endPosition 50 - replace text form 0 to 50 position by ""
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
    Args.parse(options, extractCommandArgument(PREFIX).split(" "));

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
          int startPosition = options.startPosition == null ? 0 : options.startPosition;
          int endPosition = options.endPosition == null ? document.getTextLength() : options.endPosition;
          WriteCommandAction.runWriteCommandAction(context.getProject(),
                                                   () -> document.replaceString(startPosition, endPosition, options.newText));
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
    public Integer startPosition;

    @Argument
    public Integer endPosition;

    @Argument
    public String newText = "";
  }
}
