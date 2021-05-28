// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.SpaceProjectContext

class SpaceShowReviewsAction : DumbAwareAction(SpaceBundle.messagePointer("action.show.code.reviews.text")) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = isAssociatedWithSpaceRepository(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    showCodeReviews(project)
  }

  private fun isAssociatedWithSpaceRepository(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    return SpaceProjectContext.getInstance(project).currentContext.isAssociatedWithSpaceRepository
  }

  companion object {
    internal fun showCodeReviews(project: Project) {
      ToolWindowManager
        .getInstance(project)
        .getToolWindow(SpaceReviewToolWindowFactory.ID)
        ?.show()
    }
  }
}

