package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import static com.jetbrains.performancePlugin.commands.ExitAppCommand.writeExitMetricsIfNeeded;

public final class ExitAppWithTimeoutCommand extends AbstractCallbackBasedCommand {

  public static final @NonNls String PREFIX = CMD_PREFIX + "exitAppWithTimeout";

  public ExitAppWithTimeoutCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected void execute(@NotNull ActionCallback callback, @NotNull PlaybackContext context) {
    writeExitMetricsIfNeeded();

    String[] arguments = getText().split(" ", 2);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        TimeUnit.SECONDS.sleep(Long.parseLong(arguments[1]));
        ApplicationManager.getApplication().exit(true, true, false);
        callback.setDone();
      }
      catch (InterruptedException e) {
        callback.reject(e.getMessage());
      }
    });
  }
}