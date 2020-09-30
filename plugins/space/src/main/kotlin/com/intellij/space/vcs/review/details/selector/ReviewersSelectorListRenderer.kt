package com.intellij.space.vcs.review.details.selector

import circlet.client.api.englishFullName
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

internal class ReviewersSelectorListRenderer(private val avatarProvider: SpaceAvatarProvider) : ListCellRenderer<CheckedReviewer> {
  private val checkbox = JBCheckBox()
  private val label = SimpleColoredComponent()
  private val panel = BorderLayoutPanel(10, 5).apply {
    addToLeft(checkbox)
    addToCenter(label)
    border = JBUI.Borders.empty(5)
  }

  override fun getListCellRendererComponent(list: JList<out CheckedReviewer>,
                                            value: CheckedReviewer,
                                            index: Int,
                                            selected: Boolean,
                                            cellHasFocus: Boolean): Component {
    UIUtil.setBackgroundRecursively(panel, ListUiUtil.WithTallRow.background(list, selected, true))
    val primaryTextColor = ListUiUtil.WithTallRow.foreground(selected, list.hasFocus())

    checkbox.apply {
      this.isSelected = value.checked.value.contains(value.reviewer.id)
      isFocusPainted = cellHasFocus
    }

    label.apply {
      clear()
      append(value.reviewer.englishFullName())
      icon = avatarProvider.getIcon(value.reviewer)
      foreground = primaryTextColor
    }

    return panel
  }
}
