// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.list

import circlet.code.api.CodeReviewListItem
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.CompositeShortcutSet
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.vcs.review.ReviewUiSpec
import com.intellij.space.vcs.review.SpaceReviewDataKeys
import com.intellij.space.vcs.review.openReviewInEditor
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ListUtil
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.OpenReviewButton
import com.intellij.util.ui.codereview.OpenReviewButtonViewModel
import libraries.coroutines.extra.Lifetime
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ListSelectionModel

internal class SpaceReviewsList(
  listModel: CollectionListModel<CodeReviewListItem>,
  lifetime: Lifetime
) : JBList<CodeReviewListItem>(listModel),
    DataProvider {

  private val openButtonViewModel = OpenReviewButtonViewModel()

  private val openReviewDetailsAction = SpaceOpenCodeReviewDetailsAction()

  init {
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

    OpenReviewButton.installOpenButtonListeners(this, openButtonViewModel) {
      openReviewDetailsAction
    }
    val userAvatarProvider = SpaceAvatarProvider(lifetime, this, ReviewUiSpec.avatarSizeIntValue)

    val renderer = SpaceReviewListCellRenderer(userAvatarProvider, openButtonViewModel)
    cellRenderer = renderer
    UIUtil.putClientProperty(this, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, listOf(renderer))
    setExpandableItemsEnabled(false)
    ScrollingUtil.installActions(this)
    ListUtil.installAutoSelectOnMouseMove(this)
    ListUiUtil.Selection.installSelectionOnFocus(this)
    ListUiUtil.Selection.installSelectionOnRightClick(this)

    val shortcuts = CompositeShortcutSet(CommonShortcuts.ENTER, CommonShortcuts.DOUBLE_CLICK_1)
    openReviewDetailsAction.registerCustomShortcutSet(shortcuts, this)
  }

  override fun getToolTipText(event: MouseEvent?): String? {
    event ?: return null
    val childComponent = ListUtil.getDeepestRendererChildComponentAt(this, event.point) ?: return null
    if (childComponent !is JComponent) return null
    return childComponent.toolTipText
  }

  override fun getData(dataId: String): Any? = when {
    SpaceReviewDataKeys.SELECTED_REVIEW.`is`(dataId) -> selectedValue
    else -> null
  }

  override fun setExpandableItemsEnabled(enabled: Boolean) {
    super.setExpandableItemsEnabled(false)
  }
}

private class SpaceOpenCodeReviewDetailsAction : DumbAwareAction(SpaceBundle.messagePointer("action.open.review.details.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val data = e.getData(SpaceReviewDataKeys.SELECTED_REVIEW) ?: return
    val selectedReviewVm = e.getData(SpaceReviewDataKeys.SELECTED_REVIEW_VM) ?: return
    logOpenReview(e)

    selectedReviewVm.selectedReview.value = data

    openReviewInEditor(project, selectedReviewVm.workspace, selectedReviewVm.projectInfo, data.review)
  }

  private fun logOpenReview(e: AnActionEvent) {
    val inputEvent = e.inputEvent
    val openReviewType = if (inputEvent is MouseEvent) {
      if (inputEvent.clickCount == 1) {
        SpaceStatsCounterCollector.OpenReviewActionType.ARROW
      }
      else {
        SpaceStatsCounterCollector.OpenReviewActionType.DOUBLE_CLICK
      }
    }
    else {
      SpaceStatsCounterCollector.OpenReviewActionType.ENTER
    }

    SpaceStatsCounterCollector.OPEN_REVIEW.log(openReviewType)
  }
}