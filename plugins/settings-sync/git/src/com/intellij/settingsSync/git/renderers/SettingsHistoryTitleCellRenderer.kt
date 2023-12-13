package com.intellij.settingsSync.git.renderers

import com.intellij.settingsSync.git.table.SettingsHistoryTable
import com.intellij.settingsSync.git.table.SettingsHistoryTableRow
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil

internal class SettingsHistoryTitleCellRenderer : SettingsHistoryCellRenderer() {
  private val expandedIcon = UIUtil.getTreeExpandedIcon()
  private val collapsedIcon = UIUtil.getTreeCollapsedIcon()

  override fun customizeHistoryCellRenderer(table: SettingsHistoryTable, row: SettingsHistoryTableRow, selected: Boolean, hasFocus: Boolean, rowIndex: Int) {
    if (isExpanded(table, row)) {
      icon = expandedIcon
    } else {
      icon = collapsedIcon
    }
    iconTextGap = 4

    val titleTextAttributes: SimpleTextAttributes
    val timeTextAttributes: SimpleTextAttributes
    if (isGreyedOut(table, rowIndex)) {
      iconOpacity = 0.6f
      titleTextAttributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
      timeTextAttributes = SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
    }
    else {
      titleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
      timeTextAttributes = SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES
    }

    appendWithClipping(row.record.title, titleTextAttributes, DefaultFragmentTextClipper.INSTANCE)
    append("  ") // FIXME: Is there a better way to create a gap?
    appendWithClipping(row.record.time, timeTextAttributes, DefaultFragmentTextClipper.INSTANCE)
    if (row.record.restored != null) {
      append("  ") // FIXME: Is there a better way to create a gap?
      appendWithClipping(row.record.restored, timeTextAttributes, DefaultFragmentTextClipper.INSTANCE)
    }

    putClientProperty(ExpandableItemsHandler.RENDERER_DISABLED, true)

    val cellWidth = table.columnModel.getColumn(1).width
    if (cellWidth < computePreferredSize(false).width) {
      val record = row.record
      addTooltipTextFragment(TooltipTextFragment(record.title, false, false))
      if (record.restored != null) {
        addNewLineToTooltip()
        addTooltipTextFragment(TooltipTextFragment(record.restored, true, true))
      }
      addNewLineToTooltip()
      addTooltipTextFragment(TooltipTextFragment(record.time, true, true))
      toolTipText = buildTooltip()
    }
  }
}