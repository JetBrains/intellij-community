package com.intellij.space.vcs.review.details


import circlet.code.api.CodeReviewWithCount
import circlet.code.api.CommitSetReviewRecord
import circlet.code.api.MergeRequestRecord
import circlet.platform.client.KCircletClient
import circlet.platform.client.resolve
import circlet.platform.client.toRef
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.ReturnToListComponent
import libraries.coroutines.extra.Lifetime
import runtime.reactive.MutableProperty
import runtime.reactive.SequentialLifetimes
import java.awt.BorderLayout
import javax.swing.JPanel

internal class SpaceReviewDetails(project: Project,
                         lifetime: Lifetime,
                         private val client: KCircletClient,
                         private val repoInfo: Set<SpaceRepoInfo>,
                         private val currentReview: MutableProperty<CodeReviewWithCount?>) {
  private val sequentialLifetimes: SequentialLifetimes = SequentialLifetimes(lifetime)

  val view: JPanel = JPanel(BorderLayout()).apply {
    background = UIUtil.getListBackground()
  }

  init {
    currentReview.forEach(lifetime) { cr: CodeReviewWithCount? ->
      view.removeAll()
      if (cr == null) return@forEach
      val detailsLifetime = sequentialLifetimes.next()
      val detailsVm = when (val codeReviewRecord = cr.review.resolve()) {
        is MergeRequestRecord -> MergeRequestDetailsVm(detailsLifetime, project, codeReviewRecord.toRef(client.arena), client)
        is CommitSetReviewRecord -> CommitSetReviewDetailsVm(detailsLifetime, project, codeReviewRecord.toRef(client.arena), client)
        else -> throw IllegalArgumentException("Unable to resolve CodeReviewRecord")
      }

      val uiDisposable = Disposer.newDisposable()

      val details = TabInfo(DetailedInfoPanel(detailsVm).view).apply {
        text = SpaceBundle.message("review.tab.name.details")
        sideComponent = ReturnToListComponent.createReturnToListSideComponent(SpaceBundle.message("action.reviews.back.to.list")) {
          currentReview.value = null
        }
      }
      val commits = TabInfo(SpaceReviewCommitListPanel(detailsVm, repoInfo).view).apply {
        text = SpaceBundle.message("review.tab.name.commits")
        sideComponent = ReturnToListComponent.createReturnToListSideComponent(SpaceBundle.message("action.reviews.back.to.list")) {
          currentReview.value = null
        }
      }
      val tabs = object : SingleHeightTabs(project, uiDisposable) {
        override fun adjust(each: TabInfo?) {}
      }.apply {
        addTab(details)
        addTab(commits)
      }

      view.add(tabs, BorderLayout.CENTER)
      view.validate()
      view.repaint()
    }
  }
}

