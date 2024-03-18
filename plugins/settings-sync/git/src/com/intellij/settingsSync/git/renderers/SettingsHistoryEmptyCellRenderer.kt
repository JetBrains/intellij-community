package com.intellij.settingsSync.git.renderers

import com.intellij.settingsSync.git.table.SettingsHistoryTable
import com.intellij.settingsSync.git.table.SettingsHistoryTableRow

internal class SettingsHistoryEmptyCellRenderer : SettingsHistoryCellRenderer() {
  override fun customizeHistoryCellRenderer(table: SettingsHistoryTable,
                                            row: SettingsHistoryTableRow,
                                            selected: Boolean,
                                            hasFocus: Boolean,
                                            rowIndex: Int) {
    // Nothing here. The cell is empty
  }
}