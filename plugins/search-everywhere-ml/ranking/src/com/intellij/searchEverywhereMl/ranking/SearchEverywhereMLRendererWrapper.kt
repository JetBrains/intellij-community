package com.intellij.searchEverywhereMl.ranking


import com.intellij.ide.actions.searcheverywhere.SearchListModel
import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer


internal class SearchEverywhereMLRendererWrapper(private val delegateRenderer: ListCellRenderer<Any>,
                                                 private val listModel: SearchListModel) : ListCellRenderer<Any> {
  override fun getListCellRendererComponent(list: JList<*>?,
                                            value: Any,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val component = delegateRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

    val elementInfo = listModel.getRawFoundElementAt(index)

    if (elementInfo is SearchEverywhereFoundElementInfoAfterDiff && elementInfo.orderingDiff != 0) {
      val text = if (elementInfo.orderingDiff > 0) " ↑${elementInfo.orderingDiff} " else " ↓${-elementInfo.orderingDiff} "
      val color: Color = if (elementInfo.orderingDiff > 0) JBColor.GREEN else JBColor.RED

      val diffLabel = JLabel(text)
      diffLabel.foreground = color

      val panel = JPanel(BorderLayout()).apply {
        add(diffLabel, BorderLayout.EAST)
        add(component, BorderLayout.CENTER)
      }
      return panel
    }

    return component
  }
}