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
import libraries.coroutines.extra.Lifetime
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

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

    val reviewsListPanel = JPanel(BorderLayout()).apply {
      add(filtersPanel.view, BorderLayout.NORTH)
      add(reviewsList, BorderLayout.CENTER)
    }

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
