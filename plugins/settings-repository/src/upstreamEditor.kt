// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.ide.actions.ActionsCollector
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.DialogManager
import com.intellij.util.text.nullize
import kotlinx.coroutines.runBlocking
import org.jetbrains.settingsRepository.actions.NOTIFICATION_GROUP
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JTextField

fun validateUrl(url: String?, project: Project?): String? {
  return if (url == null) "URL is empty" else icsManager.repositoryService.checkUrl(url, project)
}

internal fun createMergeActions(project: Project?, urlTextField: TextFieldWithBrowseButton, dialogManager: DialogManager): List<Action> {
  var syncTypes = SyncType.values()
  if (SystemInfo.isMac) {
    syncTypes = syncTypes.reversedArray()
  }
  // TextFieldWithBrowseButton not painted correctly if specified as validation info component, so, use textField directly
  return syncTypes.map { SyncAction(it, urlTextField.textField, project, dialogManager) }
}

private class SyncAction(private val syncType: SyncType,
                         private val urlTextField: JTextField,
                         private val project: Project?,
                         private val dialogManager: DialogManager) : AbstractAction(icsMessage("action.${syncType.messageKey}Settings.text")) {
  private fun saveRemoteRepositoryUrl(): ValidationInfo? {
    val url = urlTextField.text.nullize(true)
    validateUrl(url, project)?.let {
      return createError(it)
    }

    val repositoryManager = icsManager.repositoryManager
    repositoryManager.createRepositoryIfNeed()
    repositoryManager.setUpstream(url, null)
    return null
  }

  private fun createError(message: String) = ValidationInfo(message, urlTextField)

  override fun actionPerformed(event: ActionEvent) {
    dialogManager.performAction {
      runBlocking {
        doSync()
      }
    }
  }

  private suspend fun doSync(): List<ValidationInfo>? {
    val icsManager = icsManager
    ActionsCollector.getInstance().record("Ics.${getValue(Action.NAME)}", IcsManager::class.java)
    val isRepositoryWillBeCreated = !icsManager.repositoryManager.isRepositoryExists()
    var upstreamSet = false
    try {
      saveRemoteRepositoryUrl()?.let {
        if (isRepositoryWillBeCreated) {
          // remove created repository
          icsManager.repositoryManager.deleteRepository()
        }
        return listOf(it)
      }

      upstreamSet = true

      if (isRepositoryWillBeCreated) {
        icsManager.setApplicationLevelStreamProvider()
      }

      if (isRepositoryWillBeCreated && syncType != SyncType.OVERWRITE_LOCAL) {
        ApplicationManager.getApplication().saveSettings()
        icsManager.sync(syncType, project) { copyLocalConfig() }
      }
      else {
        icsManager.sync(syncType, project, null)
      }
    }
    catch (e: Throwable) {
      if (isRepositoryWillBeCreated) {
        // remove created repository
        icsManager.repositoryManager.deleteRepository()
      }

      LOG.warn(e)

      if (!upstreamSet || e is NoRemoteRepositoryException) {
        return listOf(createError(icsMessage("set.upstream.failed.message", e.message)))
      }
      else {
        return listOf(createError(e.message ?: "Internal error"))
      }
    }

    NOTIFICATION_GROUP.createNotification(icsMessage("sync.done.message"), NotificationType.INFORMATION).notify(project)
    return emptyList()
  }
}