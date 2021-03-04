// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.list

import circlet.code.api.CodeReviewListItem
import circlet.platform.client.BatchResult
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.LoadableListVmImpl
import com.intellij.space.ui.bindScroll
import com.intellij.space.ui.toLoadable
import com.intellij.space.vcs.review.SpaceReviewDataKeys
import com.intellij.ui.*
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.log.ui.frame.ProgressStripe
import java.awt.Component
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JScrollPane

internal object SpaceReviewListFactory {
  fun create(parentDisposable: Disposable, listVm: SpaceReviewsListVm): JComponent {
    val listModel: CollectionListModel<CodeReviewListItem> = CollectionListModel()

    val reviewsList: SpaceReviewsList = SpaceReviewsList(listModel, listVm.lifetime).apply {
      installPopup(this)
    }

    val scrollableList: JScrollPane = ScrollPaneFactory.createScrollPane(reviewsList,
                                                                         ScrollPaneFactory.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                         ScrollPaneFactory.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = JBUI.Borders.empty()
      verticalScrollBar.isOpaque = true
      UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, false)
    }

    listVm.reviews.forEach(listVm.lifetime) { xList ->
      listModel.removeAll()
      xList.batches.forEach(listVm.lifetime) { batchResult: BatchResult<CodeReviewListItem> ->
        when (batchResult) {
          is BatchResult.More -> listModel.add(batchResult.items)
          is BatchResult.Reset -> listModel.removeAll()
        }
      }
    }

    bindScroll(listVm.lifetime, scrollableList, LoadableListVmImpl(listVm.isLoading, listVm.reviews.toLoadable()), reviewsList)

    DataManager.registerDataProvider(scrollableList) { dataId ->
      if (SpaceReviewDataKeys.REVIEWS_LIST_VM.`is`(dataId)) listVm else null
    }

    val progressStripe = ProgressStripe(
      scrollableList,
      parentDisposable,
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
    )

    listVm.isLoading.forEach(listVm.lifetime) { isLoading ->
      if (isLoading) {
        progressStripe.startLoading()
        reviewsList.emptyText
          .clear()
          .appendText(SpaceBundle.message("review.loading.reviews"))
      }
      else {
        progressStripe.stopLoading()
        reviewsList.emptyText
          .clear()
          .appendText(SpaceBundle.message("review.list.empty"))
          .appendSecondaryText(SpaceBundle.message("action.refresh.text"), SimpleTextAttributes.LINK_ATTRIBUTES, ActionListener {
            SpaceStatsCounterCollector.REFRESH_REVIEWS_ACTION.log(SpaceStatsCounterCollector.RefreshReviewsPlace.EMPTY_LIST)
            listVm.refresh()
          })
      }
    }

    return progressStripe
  }

  private fun installPopup(list: SpaceReviewsList) {
    val actionManager = ActionManager.getInstance()
    val popupHandler = object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        if (ListUtil.isPointOnSelection(list, x, y)) {
          val actionGroup = actionManager.getAction("com.intellij.space.vcs.review.list.popup") as ActionGroup
          val popupMenu = actionManager.createActionPopupMenu("Circlet.Reviews.List.Popup", actionGroup)
          popupMenu.setTargetComponent(list)
          popupMenu.component.show(comp, x, y)
        }
      }
    }
    list.addMouseListener(popupHandler)
  }
}