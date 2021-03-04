// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.stats.SpaceStatsCounterCollector

class SpaceLogoutAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = SpaceWorkspaceComponent.getInstance().workspace.value != null
    SpaceActionUtils.showIconInActionSearch(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    SpaceWorkspaceComponent.getInstance().signOut(SpaceStatsCounterCollector.LogoutPlace.ACTION)
  }
}
