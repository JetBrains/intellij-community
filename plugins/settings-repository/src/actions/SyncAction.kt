package org.jetbrains.settingsRepository.actions

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.settingsRepository.*

val NOTIFICATION_GROUP = NotificationGroup.balloonGroup(PLUGIN_NAME)

abstract class SyncAction(private val syncType: SyncType) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.getPresentation().setEnabledAndVisible(icsManager.repositoryManager.hasUpstream())
  }

  override fun actionPerformed(event: AnActionEvent) {
    syncAndNotify(syncType, event.getProject())
  }
}

fun syncAndNotify(syncType: SyncType, project: Project?, notifyIfUpToDate: Boolean = true) {
  try {
    if (icsManager.sync(syncType, project) == null && !notifyIfUpToDate) {
      return
    }
  }
  catch (e: Exception) {
    LOG.warn(e)
    NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.rejected.title"), e.getMessage() ?: "Internal error", NotificationType.ERROR, null).notify(project)
  }
  NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.done.message"), NotificationType.INFORMATION).notify(project)
}

// we don't
class MergeAction : SyncAction(SyncType.MERGE)
class ResetToTheirsAction : SyncAction(SyncType.OVERWRITE_LOCAL)
class ResetToMyAction : SyncAction(SyncType.OVERWRITE_REMOTE)

class ConfigureIcsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    icsManager.runInAutoCommitDisabledMode {
      IcsSettingsEditor(e.getProject()).show()
    }
  }

  override fun update(e: AnActionEvent) {
    e.getPresentation().setIcon(null)
  }
}