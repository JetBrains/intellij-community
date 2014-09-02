package org.jetbrains.plugins.settingsRepository.actions;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.plugins.settingsRepository.IcsBundle;
import org.jetbrains.plugins.settingsRepository.IcsManager;
import org.jetbrains.plugins.settingsRepository.SyncType;

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
      NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.rejected.title"), StringUtil.notNullize(e.getMessage(), "Internal error"), NotificationType.ERROR, null).notify(project);
      return;
    }

    NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.done.message"), NotificationType.INFORMATION).notify(project);
  }
}
