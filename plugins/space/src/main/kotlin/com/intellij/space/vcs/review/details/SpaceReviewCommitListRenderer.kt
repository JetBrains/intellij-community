package com.intellij.space.vcs.review.details

import circlet.code.api.GitCommitWithGraph
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

class SpaceCommitRenderer : ListCellRenderer<GitCommitWithGraph> {

  private val nodeComponent = CommitNodeComponent().apply {
    foreground = DefaultColorGenerator().getColor(1)
  }
  private val messageComponent = SimpleColoredComponent()
  val panel = BorderLayoutPanel().addToLeft(nodeComponent).addToCenter(messageComponent)

  override fun getListCellRendererComponent(list: JList<out GitCommitWithGraph>,
                                            value: GitCommitWithGraph,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    messageComponent.clear()
    messageComponent.append(value.commit.message,
                            SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getListForeground(isSelected, cellHasFocus)))
    SpeedSearchUtil.applySpeedSearchHighlighting(list, messageComponent, true, isSelected)

    val size = list.model.size
    when {
      size <= 1 -> nodeComponent.type = CommitNodeComponent.Type.SINGLE
      index == 0 -> nodeComponent.type = CommitNodeComponent.Type.FIRST
      index == size - 1 -> nodeComponent.type = CommitNodeComponent.Type.LAST
      else -> nodeComponent.type = CommitNodeComponent.Type.MIDDLE
    }
    panel.background = UIUtil.getListBackground(isSelected, cellHasFocus)
    return panel
  }
}