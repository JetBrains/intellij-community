// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.settingsRepository.LOG
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.icsManager
import org.jetbrains.settingsRepository.icsMessage

internal val NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Settings Repository")

internal sealed class SyncAction(private val syncType: SyncType) : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val repositoryManager = icsManager.repositoryManager
    e.presentation.isEnabledAndVisible = (repositoryManager.isRepositoryExists() && repositoryManager.hasUpstream()) ||
                                         (syncType == SyncType.MERGE && icsManager.readOnlySourcesManager.repositories.isNotEmpty())
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project
    @Suppress("DialogTitleCapitalization")
    runBlockingModal(if (project == null) ModalTaskOwner.guess() else ModalTaskOwner.project(project), icsMessage("task.sync.title")) {
      syncAndNotify(syncType, project)
    }
  }
}

private suspend fun syncAndNotify(syncType: SyncType, project: Project?) {
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
    NOTIFICATION_GROUP.createNotification(icsMessage("sync.rejected.title"), e.message ?: icsMessage("sync.internal.error"), NotificationType.ERROR).notify(project)
  }
}

internal class MergeAction : SyncAction(SyncType.MERGE)
internal class ResetToTheirsAction : SyncAction(SyncType.OVERWRITE_LOCAL)
internal class ResetToMyAction : SyncAction(SyncType.OVERWRITE_REMOTE)
