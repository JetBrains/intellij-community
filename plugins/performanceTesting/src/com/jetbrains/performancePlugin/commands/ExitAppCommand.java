package com.jetbrains.performancePlugin.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.utils.AbstractCallbackBasedCommand;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public final class ExitAppCommand extends AbstractCallbackBasedCommand {

  public static final @NonNls String PREFIX = CMD_PREFIX + "exitApp";

  public ExitAppCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected void execute(@NotNull ActionCallback callback, @NotNull PlaybackContext context) {
    writeExitMetricsIfNeeded();

    String[] arguments = getText().split(" ", 2);
    boolean forceExit = true;
    if (arguments.length > 1) {
      forceExit = Boolean.parseBoolean(arguments[1]);
    }

    ApplicationManager.getApplication().exit(forceExit, true, false);
    callback.setDone();
  }

  public static void writeExitMetricsIfNeeded() {
    String exitMetricsPath = System.getProperty("idea.log.exit.metrics.file");
    if (exitMetricsPath != null) {
      writeExitMetrics(exitMetricsPath);
    }
  }

  private static void writeExitMetrics(String path) {
    MemoryCapture capture = MemoryCapture.capture();

    MemoryMetrics memory = new MemoryMetrics(capture.getUsedMb(), capture.getMaxMb(), capture.getMetaspaceMb());
    ExitMetrics metrics = new ExitMetrics(memory);

    try {
      new ObjectMapper().writeValue(new File(path), metrics);
    }
    catch (IOException e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Unable to write exit metrics from " + ExitAppCommand.class.getSimpleName() + " " + e.getMessage());
    }
  }
}
