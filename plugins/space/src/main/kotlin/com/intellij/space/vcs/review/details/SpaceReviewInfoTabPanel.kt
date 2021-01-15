// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.CodeReviewRecord
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.utils.formatPrettyDateTime
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.space.vcs.review.details.diff.SpaceDiffFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.util.ui.components.BorderLayoutPanel

internal class SpaceReviewInfoTabPanel(
  parentDisposable: Disposable,
  detailsVm: SpaceReviewDetailsVm<out CodeReviewRecord>
) : BorderLayoutPanel() {
  init {
    val tree = SpaceReviewChangesTreeFactory.create(
      detailsVm.ideaProject,
      parentDisposable,
      this,
      detailsVm.allChangesVm,
      object : SpaceDiffFileProvider {
        override fun getSpaceDiffFile(): SpaceDiffFile {
          return SpaceDiffFile(detailsVm.selectedChangesVm, detailsVm.spaceDiffVm)
        }
      }
    ).apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }
    val treeActionsToolbarPanel = createChangesBrowserToolbar(tree)

    OnePixelSplitter(true, "space.review.info.changes", 0.3f).apply {
      firstComponent = SpaceReviewInfoPanelFactory.create(detailsVm)
      secondComponent = BorderLayoutPanel()
        .addToTop(treeActionsToolbarPanel)
        .addToCenter(tree)
    }.also {
      addToCenter(it)
    }
  }
}
