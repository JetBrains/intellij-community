// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.profilers;

import com.intellij.notification.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class ProfilerHandlerUtils {
  public static final Logger LOG = Logger.getInstance(ProfilerHandlerUtils.class);
  private static final NotificationGroup GROUP = NotificationGroupManager.getInstance().getNotificationGroup("PerformancePlugin");

  public static void notify(@Nullable Project project, File snapshot) {
    var availableSnapshotProcessors =
      SnapshotOpener.EP_NAME.getExtensionList().stream().filter(it -> it.canOpen(snapshot, project)).toList();
    Notification notification =
      GROUP.createNotification(PerformanceTestingBundle.message("profiling.capture.snapshot.success", snapshot.getName()),
                               NotificationType.INFORMATION);

    for (SnapshotOpener opener : availableSnapshotProcessors) {
      notification.addAction(NotificationAction.createSimpleExpiring(
        opener.getPresentableName(),
        () -> opener.open(snapshot, project)
      ));
    }

    notification.notify(project);
  }

  public static void notifyCapturingError(@NotNull Throwable t, @Nullable Project project) {
    LOG.warn(t);
    String text = PerformanceTestingBundle.message("profiling.capture.snapshot.error", t.getMessage());
    GROUP.createNotification(text, NotificationType.ERROR).notify(project);
  }
}
