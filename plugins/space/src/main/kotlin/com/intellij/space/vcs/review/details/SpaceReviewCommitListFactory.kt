package com.intellij.space.vcs.review.details

import circlet.code.api.CodeReviewRecord
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants


object SpaceReviewCommitListFactory {
  internal fun createCommitList(reviewDetailsVm: CrDetailsVm<out CodeReviewRecord>): JComponent {
    val listModel: CollectionListModel<ReviewCommitListItem> = CollectionListModel()

    val commitList = JBList(listModel).apply {
      selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
      val renderer = SpaceCommitRenderer()
      cellRenderer = renderer
      UIUtil.putClientProperty(this,
                               UIUtil.NOT_IN_HIERARCHY_COMPONENTS,
                               listOf(renderer.panel))
    }.also {
      ScrollingUtil.installActions(it)
      ListUiUtil.Selection.installSelectionOnRightClick(it)

      ListSpeedSearch(it) { commit -> commit.commitWithGraph.commit.message }
    }

    reviewDetailsVm.commits.forEach(reviewDetailsVm.lifetime) { commits ->
      listModel.removeAll()
      if (commits != null) {
        listModel.add(commits)
      }
    }

    commitList.addListSelectionListener {
      reviewDetailsVm.selectedCommitIndices.value = commitList.selectedIndices.toList()
    }

    return ScrollPaneFactory.createScrollPane(commitList, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }
  }
}