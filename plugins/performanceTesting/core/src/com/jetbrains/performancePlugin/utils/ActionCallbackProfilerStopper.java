// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.Strings;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import com.jetbrains.performancePlugin.PlaybackRunnerExtended;
import com.jetbrains.performancePlugin.profilers.Profiler;

import java.util.ArrayList;

public final class ActionCallbackProfilerStopper extends ActionCallback {
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
  }

  private @NlsSafe String errorText() {
    return Strings.notNullize(getError());
  }
}
