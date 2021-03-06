// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import circlet.client.api.englishFullName
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

internal class SpaceReviewParticipantSelectorListRenderer(private val avatarProvider: SpaceAvatarProvider, @Nls suggestedText: String) : ListCellRenderer<SpaceReviewParticipantItem> {
  private val checkbox: JBCheckBox = JBCheckBox()
  private val label: SimpleColoredComponent = SimpleColoredComponent()

  private val panel = BorderLayoutPanel(10, 5).apply {
    addToLeft(checkbox)
    addToCenter(label)
    border = JBUI.Borders.empty(5)
  }

  private val wrapper = Wrapper().apply {
    setContent(panel)
  }

  private val suggestedLabel: SimpleColoredComponent = SimpleColoredComponent().apply {
    append(suggestedText, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES, null)
    background = UIUtil.getListBackground()
  }

  override fun getListCellRendererComponent(list: JList<out SpaceReviewParticipantItem>,
                                            item: SpaceReviewParticipantItem,
                                            index: Int,
                                            selected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val primaryTextColor = ListUiUtil.WithTallRow.foreground(selected, list.hasFocus())

    val profile = item.profile

    checkbox.apply {
      this.isSelected = item.isSelected()
      this.isFocusPainted = cellHasFocus
      this.isFocusable = cellHasFocus
    }

    label.apply {
      clear()
      append(profile.englishFullName()) // NON-NLS
      icon = avatarProvider.getIcon(profile)
      foreground = primaryTextColor
    }

    val position = item.position

    wrapper.apply {
      border = when (position) {
        SpaceReviewParticipantItemPosition.LAST_SUGGESTED -> JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.listSeparatorColor(),
                                                                                     0, 0, 1, 0)
        SpaceReviewParticipantItemPosition.SINGLE_SUGGESTED -> JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.listSeparatorColor(),
                                                                                       0, 0, 1, 0)
        else -> null
      }
    }
    UIUtil.setBackgroundRecursively(wrapper, ListUiUtil.WithTallRow.background(list, selected, true))

    if (position != SpaceReviewParticipantItemPosition.FIRST_SUGGESTED && position != SpaceReviewParticipantItemPosition.SINGLE_SUGGESTED)
      return wrapper


    return JPanel(VerticalLayout(0)).apply {
      add(suggestedLabel)
      add(wrapper)
      UIUtil.setBackgroundRecursively(this, ListUiUtil.WithTallRow.background(list, isSelected = false, hasFocus = true))
      UIUtil.setBackgroundRecursively(wrapper, ListUiUtil.WithTallRow.background(list, selected, true))
    }
  }
}
