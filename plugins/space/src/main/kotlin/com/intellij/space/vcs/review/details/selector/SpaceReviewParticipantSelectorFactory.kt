// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.LoadableListAdapter
import com.intellij.space.ui.LoadableListVmImpl
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.ui.bindScroll
import com.intellij.space.vcs.review.ReviewUiSpec
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import org.jetbrains.annotations.Nls
import runtime.Ui
import runtime.reactive.Property
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

internal object SpaceReviewParticipantSelectorFactory {
  fun create(selectorVm: SpaceReviewParticipantSelectorVm,
             lifetime: Lifetime,
             participantController: SpaceReviewParticipantController,
             @Nls suggestedText: String): SpaceReviewComponentData {
    val model = CollectionListModel<SpaceReviewParticipantItem>()
    val list = JBList(model).apply<JBList<SpaceReviewParticipantItem>> {
      val avatarProvider = SpaceAvatarProvider(lifetime, this, ReviewUiSpec.avatarSizeIntValue)
      visibleRowCount = ReviewUiSpec.ReviewersSelector.VISIBLE_ROWS_COUNT
      isFocusable = false
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      cellRenderer = SpaceReviewParticipantSelectorListRenderer(avatarProvider, suggestedText)

      addMouseListener(object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
          if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed) {
            selectReviewers(lifetime, participantController)
            repaint()
          }
        }
      })
    }
    val scrollPane = ScrollPaneFactory.createScrollPane(list, true).apply {
      isFocusable = false
    }

    selectorVm.dataUpdateSignal.forEach(lifetime) { update ->
      when (update) {
        DataUpdate.RemoveAll -> model.removeAll()
        is DataUpdate.AppendParticipants -> model.add(update.data)
        is DataUpdate.PrependParticipants -> model.addAll(0, update.data)
      }
    }

    selectorVm.isLoading.forEach(lifetime) { isLoading ->
      list.setPaintBusy(isLoading)
      list.setEmptyText(
        if (isLoading) {
          SpaceBundle.message("review.reviewers.selector.loading")
        }
        else {
          SpaceBundle.message("review.reviewers.selector.list.empty.text")
        }
      )
    }

    selectorVm.currentParticipants.forEach(lifetime) {
      model.allContentsChanged()
    }

    val searchField = SearchTextField(false).apply {
      border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
      UIUtil.setBackgroundRecursively(this, UIUtil.getListBackground())
      textEditor.border = JBUI.Borders.empty()
      //focus dark magic, otherwise focus shifts to searchfield panel
      isFocusable = false
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          selectorVm.searchParticipant(text)
        }
      })
    }
    val panel = JBUI.Panels.simplePanel(scrollPane).addToTop(searchField)
    ScrollingUtil.installActions(list, panel)
    ListUtil.installAutoSelectOnMouseMove(list)
    bindScroll<SpaceReviewParticipantItem?>(lifetime, scrollPane,
                                            LoadableListVmImpl(selectorVm.isLoading, Property.create(object : LoadableListAdapter {
                                              override fun hasMore(): Boolean {
                                                return selectorVm.possibleParticipants.value.hasMore.value
                                              }

                                              override suspend fun more() {
                                                selectorVm.possibleParticipants.value.more()
                                              }

                                            })), list)
    return SpaceReviewComponentData(panel, searchField, ActionListener { list.selectReviewers(lifetime, participantController) })
  }
}

data class SpaceReviewComponentData(
  val component: JComponent,
  val focusableComponent: JComponent,
  val clickListener: ActionListener
)

internal fun JBList<SpaceReviewParticipantItem>.selectReviewers(lifetime: Lifetime, controller: SpaceReviewParticipantController) {
  for (item in selectedValuesList) {
    launch(lifetime, Ui) {
      if (item.isSelected()) {
        controller.removeParticipant(item.profile)
      }
      else {
        controller.addParticipant(item.profile)
      }
    }
  }
}
