// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.CodeReviewRecord
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.space.editor.SpaceVirtualFilesManager
import com.intellij.space.vcs.review.details.diff.SpaceDiffFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.util.ui.UIUtil
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
          return detailsVm.ideaProject.service<SpaceVirtualFilesManager>()
            .findOrCreateDiffFile(detailsVm.selectedChangesVm, detailsVm.spaceDiffVm)
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
      isOpaque = true
      background = UIUtil.getListBackground()
    }.also {
      addToCenter(it)
    }
  }
}
