package com.jetbrains.performancePlugin.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.Strings;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.PlaybackRunnerExtended;
import com.jetbrains.performancePlugin.profilers.Profiler;

import java.util.ArrayList;

public final class ActionCallbackProfilerStopper extends ActionCallback {
  private static final Logger LOG = Logger.getInstance(ActionCallbackProfilerStopper.class);

  @Override
  public void setRejected() {
    super.setRejected();
    try {
      Profiler.getCurrentProfilerHandler().stopProfiling(new ArrayList<>());
    }
    catch (Exception ignored) {
    }
    final Notification errorNotification = new Notification(PlaybackRunnerExtended.NOTIFICATION_GROUP,
                                                            PerformanceTestingBundle.message("callback.stop"),
                                                            errorText(),
                                                            NotificationType.ERROR);
    Notifications.Bus.notify(errorNotification);
    LOG.error(getError());
  }

  @NlsSafe
  private String errorText() {
    return Strings.notNullize(getError());
  }
}
