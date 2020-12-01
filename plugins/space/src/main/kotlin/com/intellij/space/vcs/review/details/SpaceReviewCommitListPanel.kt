// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.CodeReviewRecord
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent

internal class SpaceReviewCommitListPanel(reviewDetailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>) : BorderLayoutPanel() {
  init {
    val commitsBrowser = OnePixelSplitter(true, "space.review.commit.list", 0.7f).apply {
      firstComponent = SpaceReviewCommitListFactory.createCommitList(reviewDetailsVm)


      val tree = SpaceReviewChangesTreeFactory.create(reviewDetailsVm.ideaProject,
                                                      this,
                                                      reviewDetailsVm.changesVm,
                                                      reviewDetailsVm.spaceDiffVm)
      val treeActionsToolbarPanel = createChangesBrowserToolbar(tree)
      secondComponent = BorderLayoutPanel()
        .addToTop(treeActionsToolbarPanel)
        .addToCenter(tree)
    }
    addToCenter(commitsBrowser)
  }

  private fun createChangesBrowserToolbar(target: JComponent): TreeActionsToolbarPanel {
    val actionManager = ActionManager.getInstance()
    val changesToolbarActionGroup = actionManager.getAction("space.review.changes.toolbar") as ActionGroup
    val changesToolbar = actionManager.createActionToolbar("ChangesBrowser", changesToolbarActionGroup, true)
    val treeActionsGroup = DefaultActionGroup(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL),
                                              actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL))
    return TreeActionsToolbarPanel(changesToolbar, treeActionsGroup, target)
  }

}


