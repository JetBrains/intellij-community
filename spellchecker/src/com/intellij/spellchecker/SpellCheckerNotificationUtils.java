// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.intellij.notification.*;
import com.intellij.openapi.project.Project;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;

import static com.intellij.notification.NotificationType.INFORMATION;

public class SpellCheckerNotificationUtils {
  private static final NotificationGroup NOTIFICATION_GROUP =
    NotificationGroup.balloonGroup(SpellCheckerBundle.message("spellchecker"));

  static void showNotification(final Project project,
                               @NotNull String title,
                               @NotNull String message,
                               @NotNull NotificationAction... actions) {
    final Notification notification =
      new Notification(NOTIFICATION_GROUP.getDisplayId(), title, message, INFORMATION, null);
    for (NotificationAction action : actions) {
      notification.addAction(action);
    }
    Notifications.Bus.notify(notification, project);
  }
}
