package com.intellij.space.vcs.review.details

import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.commits.CommitNodeComponent
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.log.graph.DefaultColorGenerator
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

class SpaceCommitRenderer : ListCellRenderer<ReviewCommitListItem> {

  private val nodeComponent: CommitNodeComponent = CommitNodeComponent().apply {
    foreground = DefaultColorGenerator().getColor(1)
  }
  private val messageComponent: SimpleColoredComponent = SimpleColoredComponent()

  private val repositorySeparator = SimpleColoredComponent().apply {

  }


  var panel: BorderLayoutPanel = BorderLayoutPanel().addToLeft(nodeComponent).addToCenter(messageComponent)


  override fun getListCellRendererComponent(list: JList<out ReviewCommitListItem>,
                                            value: ReviewCommitListItem,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    panel.removeAll()
    panel.addToLeft(nodeComponent).addToCenter(messageComponent)

    if (value.index == 0) {
      value.inCurrentProject
      repositorySeparator.clear()

      val attr =
        if (value.inCurrentProject) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        else SimpleTextAttributes.GRAYED_ATTRIBUTES

      repositorySeparator.append(value.repositoryInReview.name, attr)
      panel.addToTop(repositorySeparator)
    }

    messageComponent.clear()
    messageComponent.append(value.commitWithGraph.commit.message,
                            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getListForeground(isSelected, true)))
    SpeedSearchUtil.applySpeedSearchHighlighting(list, messageComponent, true, isSelected)

    val size = value.commitsInRepository
    when {
      size <= 1 -> nodeComponent.type = CommitNodeComponent.Type.SINGLE
      value.index == 0 -> nodeComponent.type = CommitNodeComponent.Type.FIRST
      value.index == size - 1 -> nodeComponent.type = CommitNodeComponent.Type.LAST
      else -> nodeComponent.type = CommitNodeComponent.Type.MIDDLE
    }
    panel.background = UIUtil.getListBackground(isSelected, true)
    repositorySeparator.background = UIUtil.getListBackground(false, true)

    return panel
  }
}