// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details


import circlet.code.api.CodeReviewListItem
import circlet.workspaces.Workspace
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.SpaceReviewDataKeys
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.ReturnToListComponent
import com.intellij.util.ui.components.BorderLayoutPanel
import libraries.coroutines.extra.Lifetime
import runtime.reactive.MutableProperty
import runtime.reactive.SequentialLifetimes

internal class SpaceReviewDetails(parentDisposable: Disposable,
                                  project: Project,
                                  lifetime: Lifetime,
                                  private val workspace: Workspace,
                                  private val spaceProjectInfo: SpaceProjectInfo,
                                  private val repoInfo: Set<SpaceRepoInfo>,
                                  private val currentReview: MutableProperty<CodeReviewListItem?>) {
  private val sequentialLifetimes: SequentialLifetimes = SequentialLifetimes(lifetime)

  val view: BorderLayoutPanel = BorderLayoutPanel().apply {
    background = UIUtil.getListBackground()
  }

  init {
    var uiDisposable: Disposable? = null

    currentReview.forEach(lifetime) { reviewListItem: CodeReviewListItem? ->
      view.removeAll()

      uiDisposable?.let { Disposer.dispose(it) }
      if (reviewListItem == null) return@forEach
      val detailsLifetime = sequentialLifetimes.next()
      uiDisposable = Disposer.newDisposable()

      val detailsVm = createReviewDetailsVm(uiDisposable!!, detailsLifetime, project, workspace, spaceProjectInfo, repoInfo, reviewListItem)

      Disposer.register(parentDisposable, uiDisposable as Disposable)

      val detailsTabInfo = TabInfo(SpaceReviewInfoTabPanel(parentDisposable, detailsVm)).apply {
        text = SpaceBundle.message("review.tab.name.details")
        sideComponent = ReturnToListComponent.createReturnToListSideComponent(SpaceBundle.message("action.reviews.back.to.list")) {
          SpaceStatsCounterCollector.BACK_TO_LIST.log(SpaceStatsCounterCollector.DetailsTabType.DETAILS)
          currentReview.value = null
        }
      }
      val commitsTabInfo = TabInfo(SpaceReviewCommitListPanel(parentDisposable, detailsVm)).apply {
        text = SpaceBundle.message("review.tab.name.commits")
        sideComponent = ReturnToListComponent.createReturnToListSideComponent(SpaceBundle.message("action.reviews.back.to.list")) {
          SpaceStatsCounterCollector.BACK_TO_LIST.log(SpaceStatsCounterCollector.DetailsTabType.COMMITS)
          currentReview.value = null
        }
      }

      detailsVm.commits.forEach(lifetime) { commits ->
        val hasUnreachableCommits: Boolean = commits.any { it.commitWithGraph.unreachable }
        if (hasUnreachableCommits) {
          commitsTabInfo.icon = AllIcons.General.Warning
          commitsTabInfo.tooltipText = SpaceBundle.message("review.tab.name.commits.warning.unreachable.commits")
        }
        else {
          commitsTabInfo.icon = null
          commitsTabInfo.tooltipText = null
        }

        commitsTabInfo.text = SpaceBundle.message("review.tab.name.commits.count", commits.size)
      }

      val tabs = object : SingleHeightTabs(project, uiDisposable as Disposable) {
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

        addListener(object : TabsListener {
          override fun selectionChanged(oldSelection: TabInfo, newSelection: TabInfo) {
            detailsVm.selectedTab.value = when (newSelection.component) {
              is SpaceReviewCommitListPanel -> {
                SpaceStatsCounterCollector.SELECT_DETAILS_TAB.log(SpaceStatsCounterCollector.DetailsTabType.COMMITS)
                SelectedTab.COMMITS
              }
              else -> {
                SpaceStatsCounterCollector.SELECT_DETAILS_TAB.log(SpaceStatsCounterCollector.DetailsTabType.DETAILS)
                SelectedTab.INFO
              }
            }
          }
        })
      }

      view.addToCenter(tabs)
      view.validate()
      view.repaint()
    }
  }
}

