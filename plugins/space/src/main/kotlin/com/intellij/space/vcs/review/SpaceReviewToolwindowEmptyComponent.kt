// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import com.intellij.openapi.project.Project
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.vcs.SpaceProjectContext
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import libraries.coroutines.extra.Lifetime
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import runtime.reactive.Property
import runtime.reactive.isLoading
import runtime.reactive.property.map
import javax.swing.JPanel

internal class SpaceReviewToolwindowEmptyComponent(project: Project, lifetime: Lifetime) : JPanel() {
  private val workspaceComponent = SpaceWorkspaceComponent.getInstance()
  private val projectContext = SpaceProjectContext.getInstance(project)

  private val emptyState: Property<EmptyState> = lifetime.map(workspaceComponent.workspace, projectContext.context) { workspace, context ->
    when {
      workspace == null -> EmptyState.NOT_LOGGED_IN
      context.isLoading -> EmptyState.CONNECTING_TO_REPOS
      else -> EmptyState.NOT_ASSOCIATED
    }
  }

  init {
    background = UIUtil.getListBackground()
    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .fill())
    val loginLabel = ActionLink(SpaceBundle.message("action.com.intellij.space.actions.SpaceLoginAction.text")) {
      SpaceStatsCounterCollector.REVIEWS_LOG_IN_LINK.log(project)
      SpaceSettingsPanel.openSettings(null)
    }
    val connectingToReposLabel = JBLabel(SpaceBundle.message("review.toolwindow.empty.connecting.to.repositories.label")).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    val isNotAssociatedLabel = JBLabel(SpaceBundle.message("review.toolwindow.empty.is.not.associated.label")).apply {
      foreground = UIUtil.getContextHelpForeground()
    }

    emptyState.forEach(lifetime) {
      removeAll()
      val component = when (it) {
        EmptyState.NOT_LOGGED_IN -> loginLabel
        EmptyState.CONNECTING_TO_REPOS -> connectingToReposLabel
        EmptyState.NOT_ASSOCIATED -> isNotAssociatedLabel
      }
      add(component, CC().alignX("center").alignY("center"))
      revalidate()
      repaint()
    }
  }

  private enum class EmptyState {
    NOT_LOGGED_IN,
    CONNECTING_TO_REPOS,
    NOT_ASSOCIATED
  }
}
