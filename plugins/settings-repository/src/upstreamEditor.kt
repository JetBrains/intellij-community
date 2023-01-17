// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.components.DialogManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.nullize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.settingsRepository.actions.NOTIFICATION_GROUP
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JTextField

@NlsContexts.DialogMessage
internal fun validateUrl(url: String?, project: Project?): String? {
  return if (url == null) IcsBundle.message("dialog.error.message.url.empty") else icsManager.repositoryService.checkUrl(url, project)
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
                         private val dialogManager: DialogManager) : AbstractAction(icsMessage(syncType.messageKey)) {
  override fun actionPerformed(event: ActionEvent) {
    dialogManager.performAction {
      val owner = project?.let(ModalTaskOwner::project)
                  ?: (event.source as? Component)?.let(ModalTaskOwner::component)
                  ?: ModalTaskOwner.guess()

      val url = urlTextField.text.nullize(true)
      val error = validateUrl(url, project)
                  ?: doSync(icsManager = icsManager, project = project, syncType = syncType, url = url!!, owner = owner)

      error?.let { listOf(ValidationInfo(error, urlTextField)) }
    }
  }
}

@RequiresEdt
@VisibleForTesting
fun doSync(icsManager: IcsManager, project: Project?, syncType: SyncType, url: String, owner: ModalTaskOwner): String? {
  IcsActionsLogger.logSettingsSync(project, syncType)
  val isRepositoryWillBeCreated = !icsManager.repositoryManager.isRepositoryExists()
  var upstreamSet = false
  try {
    val repositoryManager = icsManager.repositoryManager
    repositoryManager.createRepositoryIfNeeded()
    repositoryManager.setUpstream(url, null)
    icsManager.isRepositoryActive = repositoryManager.isRepositoryExists()

    upstreamSet = true

    if (isRepositoryWillBeCreated) {
      icsManager.setApplicationLevelStreamProvider()
    }

    @Suppress("DialogTitleCapitalization")
    runBlockingModal(owner, icsMessage("task.sync.title")) {
      if (isRepositoryWillBeCreated && syncType != SyncType.OVERWRITE_LOCAL) {
        com.intellij.configurationStore.saveSettings(componentManager = ApplicationManager.getApplication(), forceSavingAllSettings = false)
        icsManager.sync(syncType = syncType, project = project) { copyLocalConfig() }
      }
      else {
        icsManager.sync(syncType = syncType, project = project, localRepositoryInitializer = null)
      }
    }
  }
  catch (e: Throwable) {
    if (isRepositoryWillBeCreated) {
      // remove created repository
      icsManager.repositoryManager.deleteRepository()
    }

    LOG.warn(e)

    if (!upstreamSet || e is NoRemoteRepositoryException) {
      return e.message?.let { icsMessage("set.upstream.failed.message", it) } ?: icsMessage("set.upstream.failed.message.without.details")
    }
    else {
      return e.message ?: IcsBundle.message("sync.internal.error")
    }
  }

  NOTIFICATION_GROUP.createNotification(icsMessage("sync.done.message"), NotificationType.INFORMATION).notify(project)
  return null
}