// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackCommandReporter;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.PlaybackRunnerExtended;
import com.jetbrains.performancePlugin.Timer;
import com.jetbrains.performancePlugin.profilers.ProfilersController;
import com.jetbrains.performancePlugin.ui.FinishScriptDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Default reporter for {@code ScriptRunner}
 * Reports time statistic for executed script
 */
public class ReporterWithTimer implements PlaybackCommandReporter {
  protected final Timer timer = new Timer();
  protected boolean isCanceled;

  @Override
  public void startOfScript(@Nullable Project project) {
    isCanceled = false;
    timer.start();
  }

  @Override
  public void scriptCanceled() {
    isCanceled = true;
  }

  @Override
  public void endOfScript(@Nullable Project project) {
    timer.stop();
    ApplicationManager
      .getApplication()
      .invokeLater(() -> {
        Notifications.Bus.notify(getDelayNotification(timer.getTotalTime(), timer.getAverageDelay(), timer.getLongestDelay()), project);
        if (!isCanceled && ProfilersController.getInstance().isStoppedByScript()) {
          new FinishScriptDialog(project).show();
        }
      });
  }

  private static @NotNull Notification getDelayNotification(long totalTime, long averageDelay, long maxDelay) {
    return new Notification(PlaybackRunnerExtended.NOTIFICATION_GROUP,
                            PerformanceTestingBundle.message("delay.notification.title"),
                            PerformanceTestingBundle.message("delay.notification.message", totalTime, averageDelay, maxDelay),
                            NotificationType.INFORMATION);
  }
}
