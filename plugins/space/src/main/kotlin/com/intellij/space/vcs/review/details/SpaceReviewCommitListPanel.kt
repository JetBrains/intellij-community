// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.code.api.CodeReviewRecord
import com.intellij.ui.OnePixelSplitter
import java.awt.BorderLayout
import javax.swing.JPanel

internal class SpaceReviewCommitListPanel(detailsDetailsVm: CrDetailsVm<out CodeReviewRecord>) {
  val view: JPanel = JPanel(BorderLayout())

  init {
    val commitsBrowser = OnePixelSplitter(true, "space.review.commit.list", 0.7f).apply {
      firstComponent = SpaceReviewCommitListFactory.createCommitList(detailsDetailsVm)
      secondComponent = SpaceReviewChangesTreeFactory.create(detailsDetailsVm.ideaProject, detailsDetailsVm)
    }
    view.add(commitsBrowser, BorderLayout.CENTER)
  }
}


