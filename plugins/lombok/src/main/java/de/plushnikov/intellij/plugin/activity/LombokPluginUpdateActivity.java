package de.plushnikov.intellij.plugin.activity;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.Version;
import de.plushnikov.intellij.plugin.settings.LombokSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Shows update notification
 */
public class LombokPluginUpdateActivity implements StartupActivity, DumbAware {

  @Override
  public void runActivity(@NotNull Project project) {
    final LombokSettings settings = LombokSettings.getInstance();
    boolean updated = !Version.PLUGIN_VERSION.equals(settings.getVersion());
    if (updated) {
      settings.setVersion(Version.PLUGIN_VERSION);

      NotificationGroup group = new NotificationGroup(Version.PLUGIN_NAME, NotificationDisplayType.STICKY_BALLOON, true);
      Notification notification = group.createNotification(
        LombokBundle.message("daemon.donate.title", Version.PLUGIN_VERSION),
        LombokBundle.message("daemon.donate.content"),
        NotificationType.INFORMATION,
        new NotificationListener.UrlOpeningListener(false)
      );

      Notifications.Bus.notify(notification, project);
    }
  }

}
