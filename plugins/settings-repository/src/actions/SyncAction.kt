/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository.actions

import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jetbrains.settingsRepository.*

internal val NOTIFICATION_GROUP = NotificationGroup.balloonGroup(PLUGIN_NAME)

internal abstract class SyncAction(private val syncType: SyncType) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val repositoryManager = icsManager.repositoryManager
    e.presentation.isEnabledAndVisible = repositoryManager.isRepositoryExists() && repositoryManager.hasUpstream()
  }

  override fun actionPerformed(event: AnActionEvent) {
    syncAndNotify(syncType, event.project)
  }
}

fun syncAndNotify(syncType: SyncType, project: Project?, notifyIfUpToDate: Boolean = true) {
  try {
    if (icsManager.sync(syncType, project) == null && !notifyIfUpToDate) {
      return
    }
    NOTIFICATION_GROUP.createNotification(icsMessage("sync.done.message"), NotificationType.INFORMATION).notify(project)
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
    icsManager.runInAutoCommitDisabledMode {
      IcsSettingsPanel(e.project).show()
    }
  }

  override fun update(e: AnActionEvent) {
    if (icsManager.repositoryActive) {
      e.presentation.isEnabledAndVisible = true
    }
    else {
      val application = ApplicationManager.getApplication()
      val provider = (application.stateStore.stateStorageManager as StateStorageManagerImpl).streamProvider
      e.presentation.isEnabledAndVisible = provider == null || !provider.enabled
    }
    e.presentation.icon = null
  }
}