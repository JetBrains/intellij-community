package org.jetbrains.plugins.settingsRepository.actions

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.settingsRepository.IcsBundle
import org.jetbrains.plugins.settingsRepository.IcsManager
import org.jetbrains.plugins.settingsRepository.SyncType
import org.jetbrains.plugins.settingsRepository.PLUGIN_NAME

private val NOTIFICATION_GROUP = NotificationGroup.balloonGroup(PLUGIN_NAME)

class SyncAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.getPresentation().setEnabledAndVisible(IcsManager.getInstance().repositoryManager.hasUpstream())
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.getProject()
    try {
      IcsManager.getInstance().sync(SyncType.MERGE, project)
    }
    catch (e: Exception) {
      NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.rejected.title"), e.getMessage() ?: "Internal error", NotificationType.ERROR, null).notify(project)
      return
    }


    NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.done.message"), NotificationType.INFORMATION).notify(project)
  }
}