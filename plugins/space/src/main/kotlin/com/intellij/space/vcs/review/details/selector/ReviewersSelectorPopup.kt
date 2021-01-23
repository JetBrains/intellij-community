// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import circlet.platform.client.BatchResult
import circlet.platform.client.resolve
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.space.ui.LoadableListVmImpl
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.ui.bindScroll
import com.intellij.space.ui.toLoadable
import com.intellij.space.vcs.review.ReviewUiSpec
import com.intellij.space.vcs.review.details.SpaceReviewParticipantsVm
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import runtime.Ui
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

internal fun showPopup(selectorVm: SpaceReviewersSelectorVm,
                       lifetime: Lifetime,
                       parent: JComponent,
                       participantsVm: SpaceReviewParticipantsVm) {
  val model = CollectionListModel<CheckedReviewer>()
  val list = JBList(model).apply<JBList<CheckedReviewer>> {
    val avatarProvider = SpaceAvatarProvider(lifetime, this, ReviewUiSpec.avatarSizeIntValue)
    visibleRowCount = ReviewUiSpec.ReviewersSelector.VISIBLE_ROWS_COUNT
    isFocusable = false
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = ReviewersSelectorListRenderer(avatarProvider)

    addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed) {
          for (item in selectedValuesList) {
            launch(lifetime, Ui) {
              val isReviewer = item.checked.value.contains(item.reviewer.id)
              if (isReviewer) {
                participantsVm.removeReviewer(item.reviewer)
              } else {
                participantsVm.addReviewer(item.reviewer)
              }
            }
          }
          repaint()
        }
      }
    })
  }
  val scrollPane = ScrollPaneFactory.createScrollPane(list, true).apply {
    isFocusable = false
  }
  selectorVm.reviewersIds.forEach(lifetime) {
    model.allContentsChanged()
  }

  selectorVm.possibleReviewers.forEach(lifetime) {
    model.removeAll()
    it.isLoading.forEach(lifetime) { isLoading ->
      list.setPaintBusy(isLoading)
    }
    val authors = participantsVm.authors.value.map { author -> author.user.resolve() }.toSet()
    it.batches.forEach(lifetime) { batchResult ->
      when (batchResult) {
        is BatchResult.More -> model.add(batchResult.items.filter { item -> item.reviewer !in authors })
        is BatchResult.Reset -> model.removeAll()
      }
    }
  }

  participantsVm.authors.forEach(lifetime) { authors ->
    val possibleReviewers = model.items.map { it.reviewer }
    authors.forEach { authorParticipant ->
      val author = authorParticipant.user.resolve()
      val authorIndex = possibleReviewers.indexOf(author)
      if (authorIndex != -1) {
        model.remove(authorIndex)
      }
    }
  }

  val searchField = SearchTextField(false).apply {
    border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    UIUtil.setBackgroundRecursively(this, UIUtil.getListBackground())
    textEditor.border = JBUI.Borders.empty()
    //focus dark magic, otherwise focus shifts to searchfield panel
    isFocusable = false
    addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        selectorVm.searchText.value = text
      }
    })
  }
  val panel = JBUI.Panels.simplePanel(scrollPane).addToTop(searchField)
  ScrollingUtil.installActions(list, panel)
  ListUtil.installAutoSelectOnMouseMove(list)
  bindScroll<CheckedReviewer?>(lifetime, scrollPane, LoadableListVmImpl(selectorVm.isLoading, selectorVm.possibleReviewers.toLoadable()), list)

  JBPopupFactory.getInstance().createComponentPopupBuilder(panel, searchField)
    .setRequestFocus(true)
    .setCancelOnClickOutside(true)
    .setResizable(true)
    .setMovable(true)
    .createPopup()
    .showUnderneathOf(parent)
}


