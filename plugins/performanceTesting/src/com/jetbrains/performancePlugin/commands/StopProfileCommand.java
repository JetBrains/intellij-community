package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.profilers.Profiler;
import com.jetbrains.performancePlugin.profilers.ProfilersController;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class StopProfileCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "stopProfile";
  private static final Logger LOG = Logger.getInstance(StopProfileCommand.class);

  public StopProfileCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull PlaybackContext context) {
    ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    ProfilersController.getInstance();
    if (!Profiler.isAnyProfilingStarted()) actionCallback.reject("Profiling hasn't been started");
    try {
      String reportsPath = Profiler.getCurrentProfilerHandler().stopProfileWithNotification(actionCallback, extractCommandArgument(PREFIX));
      ProfilersController.getInstance().setReportsPath(reportsPath);
      ProfilersController.getInstance().setStoppedByScript(true);
      actionCallback.setDone();
    }
    catch (Exception exception) {
      actionCallback.reject(exception.getMessage());
      LOG.error(exception);
    }
    return Promises.toPromise(actionCallback);
  }
}
