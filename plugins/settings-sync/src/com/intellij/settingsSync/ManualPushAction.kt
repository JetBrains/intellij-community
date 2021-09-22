package com.intellij.settingsSync

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction

class ManualPushAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val facade = service<SettingsSyncFacade>()
    facade.pushSettingsToServer()
  }
}