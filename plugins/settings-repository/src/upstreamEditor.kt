/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.settingsRepository

import com.intellij.internal.statistic.ActionsCollector
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.nullize
import org.jetbrains.settingsRepository.actions.NOTIFICATION_GROUP
import java.awt.Container
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action

fun validateUrl(url: String?, project: Project?): Boolean {
  if (url == null) {
    Messages.showErrorDialog(project, "URL is empty", "")
    return false
  }

  if (!icsManager.repositoryService.checkUrl(url, project)) {
    return false
  }

  return true
}

fun createMergeActions(project: Project?, urlTextField: TextFieldWithBrowseButton, dialogParent: Container, okAction: (() -> Unit)): Array<Action> {
  var syncTypes = SyncType.values()
  if (SystemInfo.isMac) {
    syncTypes = syncTypes.reversedArray()
  }

  val icsManager = icsManager

  return Array(3) {
    val syncType = syncTypes[it]
    object : AbstractAction(icsMessage("action.${if (syncType == SyncType.MERGE) "Merge" else (if (syncType == SyncType.OVERWRITE_LOCAL) "ResetToTheirs" else "ResetToMy")}Settings.text")) {
      private fun saveRemoteRepositoryUrl(): Boolean {
        val url = urlTextField.text.nullize(true)
        if (!validateUrl(url, project)) {
          return false
        }

        val repositoryManager = icsManager.repositoryManager
        repositoryManager.createRepositoryIfNeed()
        repositoryManager.setUpstream(url, null)
        return true
      }

      override fun actionPerformed(event: ActionEvent) {
        ActionsCollector.getInstance().record("Ics.${getValue(Action.NAME)}")
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
          upstreamSet = true

          if (repositoryWillBeCreated) {
            icsManager.setApplicationLevelStreamProvider()
          }

          if (repositoryWillBeCreated && syncType != SyncType.OVERWRITE_LOCAL) {
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
            Messages.showErrorDialog(dialogParent, icsMessage("set.upstream.failed.message", e.message), icsMessage("set.upstream.failed.title"))
          }
          else {
            Messages.showErrorDialog(dialogParent, StringUtil.notNullize(e.message, "Internal error"), icsMessage(if (e is AuthenticationException) "sync.not.authorized.title" else "sync.rejected.title"))
          }
          return
        }


        NOTIFICATION_GROUP.createNotification(icsMessage("sync.done.message"), NotificationType.INFORMATION).notify(project)
        okAction()
      }
    }
  }
}