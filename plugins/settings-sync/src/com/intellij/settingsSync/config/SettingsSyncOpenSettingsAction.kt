package com.intellij.settingsSync.config

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.settingsSync.SettingsSyncBundle

open class SettingsSyncOpenSettingsAction(text: @NlsActions.ActionText String?) : DumbAwareAction(text) {

  @Suppress("Unused") // Used in plugin.xml for default base action
  constructor() : this("${SettingsSyncBundle.message("title.settings.sync")}...")

  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, SettingsSyncConfigurable::class.java)
  }
}