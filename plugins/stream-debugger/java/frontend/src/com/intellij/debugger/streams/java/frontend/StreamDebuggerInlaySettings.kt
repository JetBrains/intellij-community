// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.java.frontend

import com.intellij.debugger.streams.shared.statistics.StreamDebuggerStatisticsCollector
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener

private const val SHOW_INLAYS_SETTING_ID = "stream.debugger.show.inlays"

internal fun isStreamDebuggerInlaysEnabled(): Boolean = AdvancedSettings.getBoolean(SHOW_INLAYS_SETTING_ID)

class DisableStreamDebuggerInlay : ToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = isStreamDebuggerInlaysEnabled()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    AdvancedSettings.setBoolean(SHOW_INLAYS_SETTING_ID, state)
  }
}

internal class StreamDebuggerInlaySettingsListener : AdvancedSettingsChangeListener {
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (id != SHOW_INLAYS_SETTING_ID) return
    val enabled = newValue as? Boolean ?: return
    if (oldValue == newValue) return
    StreamDebuggerStatisticsCollector.logInlaySettingChanged(enabled)
  }
}
