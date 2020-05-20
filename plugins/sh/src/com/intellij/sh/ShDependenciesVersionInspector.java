// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.sh.formatter.ShShfmtFormatterUtil;
import com.intellij.sh.settings.ShSettings;
import com.intellij.sh.shellcheck.ShShellcheckUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.sh.ShBundle.message;
import static com.intellij.sh.ShBundle.messagePointer;

public class ShDependenciesVersionInspector implements StartupActivity.Background.DumbAware {
  public static final String NOTIFICATION_GROUP_ID = NotificationGroup.createIdWithTitle("Shell Script",
                                                                                         message("sh.title.case"));
  private static final Logger LOG = Logger.getInstance(ShDependenciesVersionInspector.class);

  @Override
  public void runActivity(@NotNull Project project) {
    assertNotDispatchThread();
    checkShfmtForUpdate(project);
    checkShellCheckForUpdate(project);
  }

  private static void checkShfmtForUpdate(@NotNull Project project) {
    if (!ShShfmtFormatterUtil.isNewVersionAvailable()) return;
    Notification notification = new Notification(NOTIFICATION_GROUP_ID, "", message("sh.fmt.update.question"),
                                                 NotificationType.INFORMATION);
    notification.addAction(
      NotificationAction.createSimple(messagePointer("sh.update"), () -> {
        notification.expire();
        ShShfmtFormatterUtil.download(project,
                                      () -> Notifications.Bus
                                        .notify(new Notification(NOTIFICATION_GROUP_ID, "", message("sh.fmt.success.update"),
                                                                 NotificationType.INFORMATION)),
                                      () -> Notifications.Bus
                                        .notify(new Notification(NOTIFICATION_GROUP_ID, "", message("sh.fmt.cannot.download"),
                                                                 NotificationType.ERROR)));
      }));
    notification.addAction(NotificationAction.createSimple(messagePointer("sh.no.thanks"), () -> {
      notification.expire();
      ShSettings.setShfmtPath(ShSettings.I_DO_MIND); // TODO FIX ME
    }));
    Notifications.Bus.notify(notification);
  }

  private static void checkShellCheckForUpdate(@NotNull Project project) {
    if (!ShShellcheckUtil.isNewVersionAvailable()) return;
    Notification notification = new Notification(NOTIFICATION_GROUP_ID, "", message("sh.shellcheck.update.question"),
                                                 NotificationType.INFORMATION);
    notification.addAction(
      NotificationAction.createSimple(messagePointer("sh.update"), () -> {
        notification.expire();
        ShShellcheckUtil.download(project,
                                  () -> Notifications.Bus
                                    .notify(new Notification(NOTIFICATION_GROUP_ID, "", message("sh.shellcheck.success.update"),
                                                             NotificationType.INFORMATION)),
                                  () -> Notifications.Bus
                                    .notify(new Notification(NOTIFICATION_GROUP_ID, "", message("sh.shellcheck.error.label"),
                                                             NotificationType.ERROR)));
      }));
    notification.addAction(NotificationAction.createSimple(messagePointer("sh.no.thanks"), () -> {
      notification.expire();
      ShSettings.setShellcheckPath(ShSettings.I_DO_MIND); // TODO FIX ME
    }));
    Notifications.Bus.notify(notification);
  }

  private static void assertNotDispatchThread() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      LOG.error("Must not be in event-dispatch thread");
    }
  }
}