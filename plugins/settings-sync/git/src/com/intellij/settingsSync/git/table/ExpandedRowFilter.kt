package com.intellij.settingsSync.git.table

import javax.swing.RowFilter

/**
 * Row filter that allows the SettingsHistory table to behave like a tree and have expandable nodes.
 * It always shows TitleRow and SeparatorRow.
 * For other rows, the filter retrieves information about expanded rows from SettingsHistoryTableModel and checks if it should be visible.
 */
internal class ExpandedRowFilter : RowFilter<SettingsHistoryTableModel, Int>() {
  override fun include(entry: Entry<out SettingsHistoryTableModel, out Int>?): Boolean {
    val row = entry?.getValue(0) as? SettingsHistoryTableRow ?: return false

    return when (row) {
      is TitleRow, is SeparatorRow -> true
      is SubtitleRow, is FileRow -> entry.model.expandedRows.contains(row.record.id)
    }
  }
}
