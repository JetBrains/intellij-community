package org.jetbrains.plugins.ideaConfigurationServer.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.plugins.ideaConfigurationServer.IcsBundle;
import org.jetbrains.plugins.ideaConfigurationServer.IcsManager;
import org.jetbrains.plugins.ideaConfigurationServer.SyncType;

class SyncAction extends DumbAwareAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(IcsManager.getInstance().getRepositoryManager().getRemoteRepositoryUrl() != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    IcsManager.getInstance().sync(SyncType.MERGE).doWhenDone(new Runnable() {
      @Override
      public void run() {
        new Notification(IcsManager.PLUGIN_NAME, IcsBundle.message("sync.done.title"), IcsBundle.message("sync.done.message"), NotificationType.INFORMATION).notify(null);
      }
    }).doWhenRejected(new Consumer<String>() {
      @Override
      public void consume(String error) {
        new Notification(IcsManager.PLUGIN_NAME,
                         IcsBundle.message("sync.rejected.title"),
                         IcsBundle.message("sync.rejected.message", StringUtil.notNullize(error, "Internal error")),
                         NotificationType.ERROR).notify(null);
      }
    });
  }
}
