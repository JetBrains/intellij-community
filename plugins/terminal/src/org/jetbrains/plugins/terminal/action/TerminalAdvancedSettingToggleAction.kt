// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAwareToggleAction

class TerminalAdvancedSettingToggleAction(private val id: String) : DumbAwareToggleAction(
  ApplicationBundle.messagePointer("advanced.setting.$id")
) {

  override fun update(e: AnActionEvent) {
    if (!ApplicationManager.getApplication().isInternal) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      super.update(e)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = AdvancedSettings.getBoolean(id)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    AdvancedSettings.setBoolean(id, state)
  }
}
