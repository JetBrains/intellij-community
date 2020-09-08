package com.intellij.space.vcs.review.list

import circlet.code.api.CodeReviewWithCount
import circlet.platform.client.BatchResult
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.space.ui.LoadableListVmImpl
import com.intellij.space.ui.bindScroll
import com.intellij.space.ui.toLoadable
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ListUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JScrollPane

object SpaceReviewListFactory {
  fun create(listVm: SpaceReviewsListVm): JComponent {
    val listModel: CollectionListModel<CodeReviewWithCount> = CollectionListModel()

    val reviewsList: SpaceReviewsList = SpaceReviewsList(listModel, listVm.lifetime).apply {
      installPopup(this)
    }

    val scrollableList: JScrollPane = ScrollPaneFactory.createScrollPane(reviewsList,
                                                                         ScrollPaneFactory.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                         ScrollPaneFactory.HORIZONTAL_SCROLLBAR_NEVER).apply {
      border = JBUI.Borders.empty()
      UIUtil.putClientProperty(verticalScrollBar, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, false)
    }

    listVm.reviews.forEach(listVm.lifetime) { xList ->
      listModel.removeAll()
      xList.batches.forEach(listVm.lifetime) { batchResult: BatchResult<CodeReviewWithCount> ->
        when (batchResult) {
          is BatchResult.More -> listModel.add(batchResult.items)
          is BatchResult.Reset -> listModel.removeAll()
        }
      }
    }

    listVm.isLoading.forEach(listVm.lifetime) {
      reviewsList.setPaintBusy(it)
    }

    bindScroll(listVm.lifetime, scrollableList, LoadableListVmImpl(listVm.isLoading, listVm.reviews.toLoadable()), reviewsList)

    DataManager.registerDataProvider(scrollableList) { dataId ->
      if (SpaceReviewListDataKeys.REVIEWS_LIST_VM.`is`(dataId)) listVm else null
    }

    return scrollableList
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