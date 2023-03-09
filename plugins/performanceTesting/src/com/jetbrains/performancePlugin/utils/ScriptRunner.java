package com.jetbrains.performancePlugin.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.ConcurrencyUtil;
import com.jetbrains.performancePlugin.CommandLogger;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.PlaybackRunnerExtended;
import com.jetbrains.performancePlugin.Timer;
import com.jetbrains.performancePlugin.profilers.ProfilersController;
import com.jetbrains.performancePlugin.ui.FinishScriptDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScriptRunner {

  private boolean isCanceled = false;

  public void doRunScript(@NotNull final Project project, @NotNull String text, @Nullable File workingDir) {
    PlaybackRunnerExtended runner = new PlaybackRunnerExtended(text, new CommandLogger(), project);
    runner.setScriptDir(workingDir);
    final Timer timer = new Timer();
    timer.start();
    Task.Backgroundable task = new Task.Backgroundable(project, PerformanceTestingBundle.message("task.title.executing.performance.script")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        runner.run().doWhenDone(() -> countDownLatch.countDown()).doWhenRejected(() -> {
          countDownLatch.countDown();
          isCanceled = true;
        });

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
        timer.stop();
        final Project finalProject = runner.getProject();
        ApplicationManager.getApplication().invokeLater(() -> {
          Notifications.Bus.notify(getDelayNotification(timer.getTotalTime(), timer.getAverageDelay(), timer.getLongestDelay()), finalProject);
          if (!isCanceled && ProfilersController.getInstance().isStoppedByScript()) {
            new FinishScriptDialog(finalProject).show();
          }
        });
      }
    };
    ProgressManager.getInstance().run(task);
  }

  @NotNull
  private static Notification getDelayNotification(long totalTime, long averageDelay, long maxDelay) {
    return new Notification(PlaybackRunnerExtended.NOTIFICATION_GROUP,
                            PerformanceTestingBundle.message("delay.notification.title"),
                            PerformanceTestingBundle.message("delay.notification.message", totalTime, averageDelay, maxDelay),
                            NotificationType.INFORMATION);
  }
}
