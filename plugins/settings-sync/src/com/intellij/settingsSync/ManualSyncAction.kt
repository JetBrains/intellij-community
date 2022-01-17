package com.intellij.settingsSync

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction

class ManualSyncAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val facade = SettingsSyncMain.getInstance()
    // todo cancellability
    // todo don't allow to start several tasks at once, including the automated ones
    object: Task.Backgroundable(e.project, SettingsSyncBundle.message("progress.title.updating.settings.from.server"), false) {
      override fun run(indicator: ProgressIndicator) {
        val updateResult = facade.controls.updateChecker.scheduleUpdateFromServer()
        when (updateResult) {
          is UpdateResult.Success -> Unit // received the update pack successfully, it will be applied async via the SettingsSyncBridge
          is UpdateResult.Error -> {
            // todo remove the error notification after next successful update
            notifySettingsSyncError(SettingsSyncBundle.message("notification.title.update.error"), updateResult.message)
            return
          }
          is UpdateResult.NoFileOnServer -> {
            notifySettingsSyncError(SettingsSyncBundle.message("notification.title.update.error"),
                                    SettingsSyncBundle.message("notification.title.update.no.such.file"))
          }
        }

      }
    }.queue()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = SettingsSyncMain.isAvailable()
  }
}