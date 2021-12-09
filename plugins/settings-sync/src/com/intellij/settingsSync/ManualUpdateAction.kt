package com.intellij.settingsSync

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction

class ManualUpdateAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val facade = service<SettingsSyncFacade>()
    // todo cancellability
    // todo don't allow to start several tasks at once, including the automated ones
    object: Task.Backgroundable(e.project, SettingsSyncBundle.message("progress.title.updating.settings.from.server"), false) {
      override fun run(indicator: ProgressIndicator) {
        facade.updateChecker.updateFromServer()
      }
    }.queue()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isSettingsSyncEnabled()
  }
}