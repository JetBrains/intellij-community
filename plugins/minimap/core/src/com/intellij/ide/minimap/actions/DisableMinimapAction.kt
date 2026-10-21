// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.actions

import com.intellij.ide.minimap.MinimapUsageCollector
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class DisableMinimapAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val settings = MinimapSettings.getInstance()
    val currentState = settings.state
    if (!currentState.enabled) return
    currentState.enabled = false
    MinimapUsageCollector.logToggled(
      enabled = false,
      source = MinimapUsageCollector.ToggleSource.ACTION_DISABLE,
      scaleMode = currentState.scaleMode,
      rightAligned = currentState.rightAligned,
    )
    settings.settingsChangeCallback.notify(MinimapSettings.SettingsChangeType.WithUiRebuild)
  }
}
