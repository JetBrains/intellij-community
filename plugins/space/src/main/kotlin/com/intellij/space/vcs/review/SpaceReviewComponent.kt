// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review

import circlet.platform.client.KCircletClient
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.details.SpaceReviewDetails
import com.intellij.space.vcs.review.list.SpaceReviewListFactory
import com.intellij.space.vcs.review.list.SpaceReviewListFiltersPanel
import com.intellij.space.vcs.review.list.SpaceReviewsListVm
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import javax.swing.JComponent

internal class SpaceReviewComponent(project: Project,
                                    lifetime: Lifetime,
                                    spaceProjectInfo: SpaceProjectInfo,
                                    repoInfo: Set<SpaceRepoInfo>,
                                    client: KCircletClient,
                                    listVm: SpaceReviewsListVm,
                                    private val selectedReviewVm: SpaceSelectedReviewVmImpl
) : Wrapper(), DataProvider {
  private val detailsView: SpaceReviewDetails = SpaceReviewDetails(
    project,
    lifetime,
    client,
    spaceProjectInfo,
    repoInfo,
    selectedReviewVm.selectedReview
  )

  init {
    val filtersPanel = SpaceReviewListFiltersPanel(listVm)
    val reviewsList: JComponent = SpaceReviewListFactory.create(listVm)

    val reviewsListPanel = BorderLayoutPanel()
      .addToTop(filtersPanel.view)
      .addToCenter(reviewsList)

    setContent(reviewsListPanel)

    selectedReviewVm.selectedReview.forEach(lifetime) { r ->
      if (r == null) {
        setContent(reviewsListPanel)
      }
      else {
        setContent(detailsView.view)
      }
      repaint()
      requestFocusInternal()
    }
  }

  override fun getData(dataId: String): Any? = when {
    SpaceReviewDataKeys.SELECTED_REVIEW_VM.`is`(dataId) -> selectedReviewVm
    else -> null
  }
}
