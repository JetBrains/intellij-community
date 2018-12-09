// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.actions

import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import org.jetbrains.settingsRepository.*
import kotlin.properties.Delegates.notNull

internal val NOTIFICATION_GROUP = NotificationGroup.balloonGroup(PLUGIN_NAME)

internal abstract class SyncAction(private val syncType: SyncType) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val repositoryManager = icsManager.repositoryManager
    e.presentation.isEnabledAndVisible = (repositoryManager.isRepositoryExists() && repositoryManager.hasUpstream()) ||
      (syncType == SyncType.MERGE && icsManager.readOnlySourcesManager.repositories.isNotEmpty())
  }

  override fun actionPerformed(event: AnActionEvent) {
    syncAndNotify(syncType, event.project)
  }
}

private fun syncAndNotify(syncType: SyncType, project: Project?) {
  try {
    val message = if (icsManager.syncManager.sync(syncType, project)) {
      icsMessage("sync.done.message")
    }
    else {
      icsMessage("sync.up.to.date.message")
    }
    NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(project)
  }
  catch (e: Exception) {
    LOG.warn(e)
    NOTIFICATION_GROUP.createNotification(icsMessage("sync.rejected.title"), e.message ?: "Internal error", NotificationType.ERROR, null).notify(project)
  }
}

internal class MergeAction : SyncAction(SyncType.MERGE)
internal class ResetToTheirsAction : SyncAction(SyncType.OVERWRITE_LOCAL)
internal class ResetToMyAction : SyncAction(SyncType.OVERWRITE_REMOTE)

internal class ConfigureIcsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    var urlTextField: TextFieldWithBrowseButton by notNull()
    icsManager.runInAutoCommitDisabledMode {
      val panel = panel {
        row(icsMessage("settings.upstream.url")) {
          urlTextField = textFieldWithBrowseButton(value = icsManager.repositoryManager.getUpstream(),
                                                   browseDialogTitle = "Choose Local Git Repository",
                                                   fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor())
        }
      }
      dialog(title = icsMessage("settings.panel.title"),
             panel = panel,
             focusedComponent = urlTextField,
             project = e.project,
             createActions = { createMergeActions(e.project, urlTextField, it) }).show()
    }
  }

  override fun update(e: AnActionEvent) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) {
      return
    }

    e.presentation.isEnabledAndVisible = icsManager.isActive || !(application.stateStore.storageManager as StateStorageManagerImpl).compoundStreamProvider.enabled
    e.presentation.icon = null
  }
}