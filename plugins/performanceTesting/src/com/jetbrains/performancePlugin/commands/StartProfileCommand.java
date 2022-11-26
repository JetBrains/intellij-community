package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.profilers.Profiler;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StartProfileCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "startProfile";

  public StartProfileCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull PlaybackContext context) {
    final ActionCallback myActionCallback = new ActionCallbackProfilerStopper();

    final String[] executedCommand = extractCommandArgument(PREFIX).split("\\s+", 2);
    final String myActivityName = executedCommand[0]; //activityName should go right after %startProfile

    if (StringUtil.isEmpty(myActivityName)) {
      myActionCallback.reject(PerformanceTestingBundle.message("command.start.error"));
    }
    else {
      try {
        List<String> parameters = executedCommand.length > 1 ? Arrays.asList(executedCommand[1].trim().split(","))
                                                             : new ArrayList<>();
        Profiler.getCurrentProfilerHandler().startProfiling(myActivityName, parameters);
        Waiter.checkCondition(() -> Profiler.isAnyProfilingStarted()).await();
        myActionCallback.setDone();
      }
      catch (Throwable e) {
        myActionCallback.reject(e.getMessage());
      }
    }

    return Promises.toPromise(myActionCallback);
  }
}
