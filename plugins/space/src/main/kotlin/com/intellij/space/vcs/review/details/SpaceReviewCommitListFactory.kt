// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.PopupHandler.installPopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitVcs
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants


object SpaceReviewCommitListFactory {
  internal fun createCommitList(
    commitListVm: SpaceReviewCommitListVm,
    commitListController: SpaceReviewCommitListController
  ): JComponent {
    val listModel: CollectionListModel<SpaceReviewCommitListItem> = CollectionListModel()

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
      installPopupHandler(it, ActionManager.getInstance().getAction("space.review.commits.popup") as ActionGroup, ActionPlaces.POPUP)
      it.setDataProvider { key ->
        when {
          VcsDataKeys.VCS_REVISION_NUMBER.`is`(key) -> it.selectedValue?.let { selectedValue ->
            val hash = selectedValue.commitWithGraph.commit.id
            VcsLogUtil.convertToRevisionNumber(HashImpl.build(hash))
          }
          VcsDataKeys.VCS.`is`(key) -> GitVcs.getKey()
          else -> null
        }
      }

      ListSpeedSearch(it) { commit -> commit.commitWithGraph.commit.message }
    }

    commitListVm.commits.forEach(commitListVm.lifetime) { commits ->
      listModel.removeAll()
      listModel.add(commits)
      commitList.setSelectionInterval(0, commits.size - 1) // select all by default
    }

    commitList.addListSelectionListener {
      val itemsCount = commitList.itemsCount
      val selectedCommitIndices = if (commitList.selectedIndices.isNotEmpty()) {
        commitList.selectedIndices.toList()
      }
      else {
        (0 until itemsCount).toList()
      }
      SpaceStatsCounterCollector.CHANGE_COMMITS_SELECTION.log(
        SpaceStatsCounterCollector.CommitsSelectionType.calculateSelectionType(selectedCommitIndices, itemsCount)
      )
      commitListController.setSelectedCommitsIndices(selectedCommitIndices)
    }

    return ScrollPaneFactory.createScrollPane(commitList, true).apply {
      isOpaque = false
      viewport.isOpaque = false
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }
  }
}