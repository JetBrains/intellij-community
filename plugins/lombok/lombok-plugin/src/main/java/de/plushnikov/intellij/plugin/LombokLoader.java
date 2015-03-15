package de.plushnikov.intellij.plugin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import de.plushnikov.intellij.plugin.settings.LombokSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Main application component, that loads Lombok support
 */
public class LombokLoader implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(LombokLoader.class.getName());

  @NotNull
  @Override
  public String getComponentName() {
    return "Lombok plugin for IntelliJ";
  }

  @Override
  public void initComponent() {
    LOG.info("Lombok plugin initialized for IntelliJ");

    showDonate();
  }

  @Override
  public void disposeComponent() {
    LOG.info("Lombok plugin disposed for IntelliJ");
  }

  private void showDonate() {
    LombokSettings settings = LombokSettings.getInstance();
    if (!settings.isDonationShown()) {

      NotificationGroup group = new NotificationGroup("Lombok plugin", NotificationDisplayType.STICKY_BALLOON, true);
      Notification notification = group.createNotification(
          LombokBundle.message("daemon.donate.title", Version.PLUGIN_VERSION),
          LombokBundle.message("daemon.donate.content"),
          NotificationType.INFORMATION,
          new NotificationListener.UrlOpeningListener(false)
      );

      Notifications.Bus.notify(notification);

      settings.setDonationShown();
    }
  }
}
