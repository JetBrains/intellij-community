// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.profilers;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@ApiStatus.Internal
public final class ProfilerHandlerUtils {
  public static final Logger LOG = Logger.getInstance(ProfilerHandlerUtils.class);

  public static void notify(@Nullable Project project, File snapshot) {
    var availableSnapshotProcessors = ContainerUtil.filter(SnapshotOpener.EP_NAME.getExtensionList(), it -> it.canOpen(snapshot, project));

    var notification = new Notification("PerformancePlugin",
                                        PerformanceTestingBundle.message("profiling.capture.snapshot.success", snapshot.getName()),
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
    new Notification("PerformancePlugin", text, NotificationType.ERROR).notify(project);
  }
}
