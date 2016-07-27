package de.plushnikov.intellij.plugin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.ProjectComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Shows update notification
 */
public class LombokPluginUpdateProjectComponent implements ProjectComponent {
  private LombokPluginApplicationComponent application;

  @Override
  public void initComponent() {
    application = LombokPluginApplicationComponent.getInstance();
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "UpdateComponent";
  }

  @Override
  public void projectOpened() {
    if (application.isUpdated() && !application.isUpdateNotificationShown()) {
      application.setUpdateNotificationShown(true);

      NotificationGroup group = new NotificationGroup(Version.PLUGIN_NAME, NotificationDisplayType.STICKY_BALLOON, true);
      Notification notification = group.createNotification(
        LombokBundle.message("daemon.donate.title", Version.PLUGIN_VERSION),
        LombokBundle.message("daemon.donate.content"),
        NotificationType.INFORMATION,
        new NotificationListener.UrlOpeningListener(false)
      );

      Notifications.Bus.notify(notification);
    }
  }

  @Override
  public void projectClosed() {

  }
}
