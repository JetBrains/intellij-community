package com.intellij.sh;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;

public final class ShNotification {
  public static final String NOTIFICATION_GROUP_ID = "Shell Script";
  public static final NotificationGroup NOTIFICATION_GROUP =
    NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);
}
