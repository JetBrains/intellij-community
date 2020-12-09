// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.clone

import com.intellij.space.actions.SpaceActionUtils
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.util.ui.cloneDialog.VcsCloneDialog

class SpaceCloneAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val projectExists = e.project != null
    e.presentation.isEnabledAndVisible = projectExists
    SpaceActionUtils.showIconInActionSearch(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    runClone(project)
  }

  companion object {
    fun runClone(project: Project) {
      val checkoutListener = ProjectLevelVcsManager.getInstance(project).compositeCheckoutListener
      val dialog = VcsCloneDialog.Builder(project).forExtension(SpaceCloneExtension::class.java)
      if (dialog.showAndGet()) {
        dialog.doClone(checkoutListener)
      }
    }
  }
}
