// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx

internal class SpaceReviewToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(toolWindow: ToolWindow) {
    (toolWindow as ToolWindowEx).project.service<SpaceCodeReviewTabManager>()
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setToHideOnEmptyContent(true)
    val spaceCodeReviewTabManager = project.service<SpaceCodeReviewTabManager>()
    spaceCodeReviewTabManager.showReviews(toolWindow.contentManager)
  }

  override fun shouldBeAvailable(project: Project): Boolean = false

  override fun isDoNotActivateOnStart(): Boolean = true

  companion object {
    const val ID = "Space Code Reviews"
  }
}
