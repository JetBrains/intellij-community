package com.jetbrains.performancePlugin.utils;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackCommandReporter;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.jetbrains.performancePlugin.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScriptRunner {
  private PlaybackCommandReporter myScriptRunnerReporter;

  public ScriptRunner setScriptRunnerReporter(@Nullable PlaybackCommandReporter scriptRunnerReporter) {
    myScriptRunnerReporter = scriptRunnerReporter;
    return this;
  }

  public void doRunScript(@NotNull final Project project, @NotNull String text, @Nullable File workingDir) {
    PlaybackRunnerExtended runner = new PlaybackRunnerExtended(text, new CommandLogger(), project);
    runner.setScriptDir(workingDir);

    runner.setCommandStartStopProcessor(myScriptRunnerReporter != null
        ? myScriptRunnerReporter
        : SystemProperties.getBooleanProperty(ReporterCommandAsTelemetrySpan.USE_SPAN_WRAPPER_FOR_COMMAND, false)
           ? new ReporterCommandAsTelemetrySpan(new ReporterWithTimer())
           : new ReporterWithTimer());

    Task.Backgroundable task = new Task.Backgroundable(project, PerformanceTestingBundle.message("task.title.executing.performance.script")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        runner.run().doWhenProcessed(countDownLatch::countDown);

        final ScheduledExecutorService myExecutor = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin script runner");
        myExecutor.scheduleWithFixedDelay(() -> {
          if (indicator.isCanceled()) {
            runner.stop();
          }
        }, 0, 100, TimeUnit.MILLISECONDS);

        try {
          countDownLatch.await();
        }
        catch (InterruptedException ignored) {
        }
        myExecutor.shutdownNow();
      }
    };
    ProgressManager.getInstance().run(task);
  }
}
