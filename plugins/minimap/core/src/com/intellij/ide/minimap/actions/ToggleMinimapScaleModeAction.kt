// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.actions

import com.intellij.ide.minimap.layout.MinimapLayoutPolicy
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareToggleAction

class ToggleMinimapScaleModeAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    if (!MinimapLayoutPolicy.supportsFitMode(editor)) {
      e.presentation.isVisible = false
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return MinimapSettings.getInstance().state.scaleMode == MinimapScaleMode.FIT
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val settings = MinimapSettings.getInstance()
    settings.state.scaleMode = if (state) MinimapScaleMode.FIT else MinimapScaleMode.FILL
    settings.settingsChangeCallback.notify(MinimapSettings.SettingsChangeType.Normal)
  }
}
