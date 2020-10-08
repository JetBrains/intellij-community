// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.space.messages.SpaceBundle

class SpaceShowReviewsAction : DumbAwareAction(SpaceBundle.messagePointer("action.show.code.reviews.text")) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = isSpaceCodeReviewEnabled()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ToolWindowManager
      .getInstance(project)
      .getToolWindow(SpaceReviewToolWindowFactory.ID)
      ?.contentManager?.let {
        SpaceCodeReviewTabManager.getInstance(project).showReviews(it)
      }
  }
}

