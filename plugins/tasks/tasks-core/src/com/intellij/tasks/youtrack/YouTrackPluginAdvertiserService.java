// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.youtrack;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.tasks.TaskBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@Service(Service.Level.APP)
final class YouTrackPluginAdvertiserService {
  private static final @NonNls String YOUTRACK_PLUGIN_ID = "com.github.jk1.ytplugin";
  private static final @NonNls String SHOW_TIME_TRACKING_NOTIFICATION = "tasks.youtrack.plugin.ad.show.time.tracking.notification";

  private boolean myNotificationWasShownPerAppSession = false;

  @NotNull
  public static YouTrackPluginAdvertiserService getInstance() {
    return ApplicationManager.getApplication().getService(YouTrackPluginAdvertiserService.class);
  }

  public synchronized void showTimeTrackingNotification() {
    if (myNotificationWasShownPerAppSession) {
      return;
    }
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    if (!propertiesComponent.getBoolean(SHOW_TIME_TRACKING_NOTIFICATION, true)) {
      return;
    }
    PluginId pluginId = PluginId.getId(YOUTRACK_PLUGIN_ID);
    if (PluginManagerCore.isPluginInstalled(pluginId)) {
      return;
    }
    Notification notification = PluginsAdvertiser.getNotificationGroup()
      .createNotification(TaskBundle.message("notification.title.more.time.tracking.features"), 
                          TaskBundle.message("notification.content.time.tracking.in.youtrack.plugin"), 
                          NotificationType.INFORMATION)
      .setDisplayId("tasks.youtrack")
      .setSuggestionType(true)
      .setListener(new NotificationListener.UrlOpeningListener(false))
      .addAction(NotificationAction.createSimpleExpiring(TaskBundle.message("notification.content.do.not.show.again"), () -> {
        propertiesComponent.setValue(SHOW_TIME_TRACKING_NOTIFICATION, "false", "true");
      }));

    notification.notify(detectCurrentProject());
    myNotificationWasShownPerAppSession = true;
  }

  @Nullable
  private static Project detectCurrentProject() {
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    return frame != null ? frame.getProject() : null;
  }
}
