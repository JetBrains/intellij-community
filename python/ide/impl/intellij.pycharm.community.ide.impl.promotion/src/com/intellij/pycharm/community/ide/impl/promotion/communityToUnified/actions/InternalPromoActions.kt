// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.pycharm.community.ide.impl.promotion.communityToUnified.PyCommunityToUnifiedPromoService
import com.intellij.pycharm.community.ide.impl.promotion.communityToUnified.PyCommunityToUnifiedShowPromoActivity
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

// TODO delete later
@ApiStatus.Internal
class ShowPyCommunityToUnifiedTooltipAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project: Project = e.project ?: return
    e.coroutineScope.launch {
      PyCommunityToUnifiedShowPromoActivity.Helper.showTooltip(project)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

@ApiStatus.Internal
class ShowPyCommunityToUnifiedPromoDialogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project: Project = e.project ?: return
    e.coroutineScope.launch {
      PyCommunityToUnifiedShowPromoActivity.Helper.showModalPromo(project)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

@ApiStatus.Internal
class ResetCommunityToUnifiedPromoStateAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    PyCommunityToUnifiedPromoService.getInstance().resetPromoState()
  }
}
