// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details


import circlet.code.api.CodeReviewListItem
import circlet.platform.client.KCircletClient
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.SpaceReviewDataKeys
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.ReturnToListComponent
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import runtime.reactive.MutableProperty
import runtime.reactive.SequentialLifetimes

internal class SpaceReviewDetails(project: Project,
                                  lifetime: Lifetime,
                                  private val client: KCircletClient,
                                  private val spaceProjectInfo: SpaceProjectInfo,
                                  private val repoInfo: Set<SpaceRepoInfo>,
                                  private val currentReview: MutableProperty<CodeReviewListItem?>) {
  private val sequentialLifetimes: SequentialLifetimes = SequentialLifetimes(lifetime)

  val view: BorderLayoutPanel = BorderLayoutPanel().apply {
    background = UIUtil.getListBackground()
  }

  init {
    currentReview.forEach(lifetime) { reviewListItem: CodeReviewListItem? ->
      view.removeAll()
      if (reviewListItem == null) return@forEach
      val detailsLifetime = sequentialLifetimes.next()
      val detailsVm = createReviewDetailsVm(detailsLifetime, project, client, spaceProjectInfo, repoInfo, reviewListItem)

      val uiDisposable = Disposer.newDisposable()

      val detailsTabInfo = TabInfo(SpaceReviewInfoTabPanel(detailsVm)).apply {
        text = SpaceBundle.message("review.tab.name.details")
        sideComponent = ReturnToListComponent.createReturnToListSideComponent(SpaceBundle.message("action.reviews.back.to.list")) {
          currentReview.value = null
        }
      }
      val commitsTabInfo = TabInfo(SpaceReviewCommitListPanel(detailsVm)).apply {
        text = SpaceBundle.message("review.tab.name.commits")
        sideComponent = ReturnToListComponent.createReturnToListSideComponent(SpaceBundle.message("action.reviews.back.to.list")) {
          currentReview.value = null
        }
      }

      detailsVm.commits.forEach(lifetime) {
        commitsTabInfo.text =
          if (it == null) SpaceBundle.message("review.tab.name.commits")
          else SpaceBundle.message("review.tab.name.commits.count", it.size)
      }

      val tabs = object : SingleHeightTabs(project, uiDisposable) {
        override fun adjust(each: TabInfo?) {}
      }.apply {
        setDataProvider { dataId ->
          when {
            SpaceReviewDataKeys.REVIEW_DETAILS_VM.`is`(dataId) -> detailsVm
            else -> null
          }
        }

        addTab(detailsTabInfo)
        addTab(commitsTabInfo)
      }

      view.addToCenter(tabs)
      view.validate()
      view.repaint()
    }
  }
}

