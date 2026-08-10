// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.java.frontend

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.options.advanced.AdvancedSettings

private const val SHOW_INLAYS_SETTING_ID = "stream.debugger.show.inlays"

internal fun isStreamDebuggerInlaysEnabled(): Boolean = AdvancedSettings.getBoolean(SHOW_INLAYS_SETTING_ID)

class DisableStreamDebuggerInlay : ToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = isStreamDebuggerInlaysEnabled()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    AdvancedSettings.setBoolean(SHOW_INLAYS_SETTING_ID, state)
  }
}
