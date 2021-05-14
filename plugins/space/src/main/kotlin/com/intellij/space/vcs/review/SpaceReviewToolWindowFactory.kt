// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.vcs.SpaceProjectContext
import libraries.coroutines.extra.LifetimeSource
import runtime.reactive.property.mapInit

internal class SpaceReviewToolWindowFactory : ToolWindowFactory, DumbAware {
  private val lifetime: LifetimeSource = LifetimeSource()

  override fun init(toolWindow: ToolWindow) {
    super.init(toolWindow)

    val project = (toolWindow as ToolWindowEx).project

    val workspace = SpaceWorkspaceComponent.getInstance().workspace
    val spaceProjectContext = SpaceProjectContext.getInstance(project).context
    val isToolwindowAvailable = lifetime.mapInit(workspace, spaceProjectContext, false) { ws, context ->
      ws ?: return@mapInit false
      return@mapInit context.isAssociatedWithSpaceRepository
    }

    isToolwindowAvailable.forEach(lifetime) { isAvailable ->
      if (isAvailable && !toolWindow.isAvailable) {
        toolWindow.isShowStripeButton = true
      }

      toolWindow.isAvailable = isAvailable
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setToHideOnEmptyContent(true)
    val spaceCodeReviewTabManager = project.service<SpaceCodeReviewTabManager>()
    spaceCodeReviewTabManager.showReviews(toolWindow.contentManager)
  }

  override fun shouldBeAvailable(project: Project): Boolean =
    SpaceProjectContext.getInstance(project).context.value.isAssociatedWithSpaceRepository

  override fun isDoNotActivateOnStart(): Boolean = true

  companion object {
    const val ID = "Space Code Reviews"
  }
}
