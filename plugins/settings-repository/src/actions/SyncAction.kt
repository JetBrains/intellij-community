package org.jetbrains.settingsRepository.actions

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.settingsRepository.IcsSettingsPanel
import org.jetbrains.settingsRepository.PLUGIN_NAME
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.IcsManager
import org.jetbrains.settingsRepository.IcsBundle
import com.intellij.openapi.project.Project
import javax.swing.Action
import java.awt.event.ActionEvent
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.settingsRepository.NoRemoteRepositoryException
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.settingsRepository.AuthenticationException
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ArrayUtil
import org.jetbrains.settingsRepository.copyLocalConfig
import javax.swing.AbstractAction
import org.jetbrains.settingsRepository.LOG
import java.awt.Container
import com.intellij.openapi.ui.TextFieldWithBrowseButton

val NOTIFICATION_GROUP = NotificationGroup.balloonGroup(PLUGIN_NAME)

abstract class SyncAction(private val syncType: SyncType) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.getPresentation().setEnabledAndVisible(IcsManager.getInstance().repositoryManager.hasUpstream())
  }

  override fun actionPerformed(event: AnActionEvent) {
    syncAndNotify(syncType, event.getProject())
  }
}

fun syncAndNotify(syncType: SyncType, project: Project?, notifyIfUpToDate: Boolean = true) {
  try {
    if (IcsManager.getInstance().sync(syncType, project) == null && !notifyIfUpToDate) {
      return
    }
  }
  catch (e: Exception) {
    NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.rejected.title"), e.getMessage() ?: "Internal error", NotificationType.ERROR, null).notify(project)
  }
  NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.done.message"), NotificationType.INFORMATION).notify(project)
}

// we don't
class MergeAction : SyncAction(SyncType.MERGE)
class ResetToTheirsAction : SyncAction(SyncType.RESET_TO_THEIRS)
class ResetToMyAction : SyncAction(SyncType.RESET_TO_MY)

class ConfigureIcsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    IcsManager.getInstance().runInAutoCommitDisabledMode {
      IcsSettingsPanel(e.getProject()).show()
    }
  }

  override fun update(e: AnActionEvent) {
    e.getPresentation().setIcon(null)
  }
}

fun createDialogActions(project: Project?, urlTextField: TextFieldWithBrowseButton, container: Container, okAction: (() -> Unit)): Array<Action> {
  var syncTypes = SyncType.values()
  if (SystemInfo.isMac) {
    syncTypes = ArrayUtil.reverseArray(syncTypes)
  }

  val icsManager = IcsManager.getInstance()

  return Array(3) {
    val syncType = syncTypes[it]
    object : AbstractAction(IcsBundle.message("action." + (if (syncType == SyncType.MERGE) "Merge" else (if (syncType == SyncType.RESET_TO_THEIRS) "ResetToTheirs" else "ResetToMy")) + "Settings.text")) {
      fun saveRemoteRepositoryUrl(): Boolean {
        val url = StringUtil.nullize(urlTextField.getText())
        if (url != null && !icsManager.repositoryService.checkUrl(url, container)) {
          return false
        }

        val repositoryManager = icsManager.repositoryManager
        repositoryManager.createRepositoryIfNeed()
        repositoryManager.setUpstream(url, null)
        return true
      }

      override fun actionPerformed(event: ActionEvent) {
        val repositoryWillBeCreated = !icsManager.repositoryManager.isRepositoryExists()
        var upstreamSet = false
        try {
          if (!saveRemoteRepositoryUrl()) {
            if (repositoryWillBeCreated) {
              // remove created repository
              icsManager.repositoryManager.deleteRepository()
            }
            return
          }
          [suppress("UNUSED_VALUE")]
          (upstreamSet = true)

          if (repositoryWillBeCreated && syncType != SyncType.RESET_TO_THEIRS) {
            ApplicationManager.getApplication().saveSettings()

            icsManager.sync(syncType, project, { copyLocalConfig() })
          }
          else {
            icsManager.sync(syncType, project, null)
          }
        }
        catch (e: Throwable) {
          if (repositoryWillBeCreated) {
            // remove created repository
            icsManager.repositoryManager.deleteRepository()
          }

          LOG.warn(e)

          if (!upstreamSet || e is NoRemoteRepositoryException) {
            Messages.showErrorDialog(container, IcsBundle.message("set.upstream.failed.message", e.getMessage()), IcsBundle.message("set.upstream.failed.title"))
          }
          else {
            Messages.showErrorDialog(container, StringUtil.notNullize(e.getMessage(), "Internal error"), IcsBundle.message(if (e is AuthenticationException) "sync.not.authorized.title" else "sync.rejected.title"))
          }
          return
        }


        NOTIFICATION_GROUP.createNotification(IcsBundle.message("sync.done.message"), NotificationType.INFORMATION).notify(project)
        okAction()
      }
    }
  }
}