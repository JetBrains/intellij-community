package com.jetbrains.performancePlugin.profilers;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.notification.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.jetbrains.performancePlugin.PerformanceTestingBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class ProfilerHandlerUtils {
  public static final Logger LOG = Logger.getInstance(ProfilerHandlerUtils.class);
  private static final NotificationGroup GROUP = NotificationGroupManager.getInstance().getNotificationGroup("PerformancePlugin");

  public static void notify(@Nullable Project project, File snapshot) {
    Notification notification =
      GROUP.createNotification(PerformanceTestingBundle.message("profiling.capture.snapshot.success", snapshot.getName()),
                               NotificationType.INFORMATION);
    notification.addAction(NotificationAction.createSimpleExpiring(
      PerformanceTestingBundle.message("profiling.capture.snapshot.action.showInFolder", RevealFileAction.getFileManagerName()),
      () -> RevealFileAction.openFile(snapshot)
    ));
    SnapshotOpener opener = SnapshotOpener.findSnapshotOpener(snapshot);
    if (opener != null && project != null) {
      notification.addAction(NotificationAction.createSimpleExpiring(
        PerformanceTestingBundle.message("profiling.capture.snapshot.action.open"),
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
