// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.settings.SpaceSettingsPanel

class SpaceLoginAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = SpaceWorkspaceComponent.getInstance().workspace.value == null
    SpaceActionUtils.showIconInActionSearch(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    SpaceSettingsPanel.openSettings(e.project)
  }
}
