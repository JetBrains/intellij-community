package org.jetbrains.plugins.ideaConfigurationServer.actions;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.plugins.ideaConfigurationServer.IcsBundle;
import org.jetbrains.plugins.ideaConfigurationServer.IcsManager;
import org.jetbrains.plugins.ideaConfigurationServer.SyncType;

class SyncAction extends DumbAwareAction {
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup(IcsManager.PLUGIN_NAME);

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(IcsManager.getInstance().getRepositoryManager().getRemoteRepositoryUrl() != null);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    Project project = event.getProject();
    try {
      IcsManager.getInstance().sync(SyncType.MERGE, project);
    }
    catch (Exception e) {
      NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.rejected.message", StringUtil.notNullize(e.getMessage(), "Internal error")), NotificationType.ERROR)
        .notify(project == null || project.isDisposed() ? null : project);
    }

    NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.done.message"), NotificationType.INFORMATION)
      .notify(project == null || project.isDisposed() ? null : project);
  }
}
