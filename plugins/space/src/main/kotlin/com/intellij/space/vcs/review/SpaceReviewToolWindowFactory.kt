package com.intellij.space.vcs.review

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.space.components.space
import libraries.coroutines.extra.LifetimeSource

internal class SpaceReviewToolWindowFactory : ToolWindowFactory, DumbAware {
  private val lifetime: LifetimeSource = LifetimeSource()

  override fun init(toolWindow: ToolWindow) {
    super.init(toolWindow)

    val project = (toolWindow as ToolWindowEx).project

    space.workspace.forEach(lifetime) { ws ->
      val available = ws != null && shouldBeAvailable(project)
      if (available && !toolWindow.isAvailable) {
        toolWindow.isShowStripeButton = true
      }

      toolWindow.isAvailable = available
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setToHideOnEmptyContent(true)
    val spaceCodeReviewTabManager = project.service<SpaceCodeReviewTabManager>()
    spaceCodeReviewTabManager.showReviews(toolWindow.contentManager)
  }

  override fun shouldBeAvailable(project: Project): Boolean = isSpaceCodeReviewEnabled()

  override fun isDoNotActivateOnStart(): Boolean = true

  companion object {
    const val ID = "Space Code Reviews"
  }
}
