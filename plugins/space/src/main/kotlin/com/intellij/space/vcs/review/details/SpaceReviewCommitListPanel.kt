// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.CodeReviewRecord
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.components.BorderLayoutPanel

internal class SpaceReviewCommitListPanel(reviewDetailsVm: CrDetailsVm<out CodeReviewRecord>) : BorderLayoutPanel() {
  init {
    val commitsBrowser = OnePixelSplitter(true, "space.review.commit.list", 0.7f).apply {
      firstComponent = SpaceReviewCommitListFactory.createCommitList(reviewDetailsVm)
      secondComponent = SpaceReviewChangesTreeFactory.create(reviewDetailsVm.ideaProject,
                                                             reviewDetailsVm.changesVm,
                                                             reviewDetailsVm.spaceDiffVm)
    }
    addToCenter(commitsBrowser)
  }
}


