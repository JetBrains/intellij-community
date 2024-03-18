package com.intellij.settingsSync.git.renderers

import com.intellij.icons.AllIcons
import com.intellij.icons.ExpUiIcons
import com.intellij.settingsSync.SettingsSyncBundle
import com.intellij.settingsSync.git.record.HistoryRecord
import com.intellij.settingsSync.git.table.SettingsHistoryTable
import com.intellij.settingsSync.git.table.SettingsHistoryTableRow
import com.intellij.settingsSync.git.table.TitleRow
import com.intellij.util.ui.JBUI

internal class SettingsHistoryRestoreCellRenderer : SettingsHistoryCellRenderer() {
  override fun customizeHistoryCellRenderer(table: SettingsHistoryTable,
                                            row: SettingsHistoryTableRow,
                                            selected: Boolean,
                                            hasFocus: Boolean,
                                            rowIndex: Int) {
    if (row !is TitleRow || !isHovered(table, row)) return

    ipad = JBUI.insetsLeft(4)
    icon = when (row.record.position) {
      HistoryRecord.RecordPosition.TOP -> {
        val numberOfChanges = row.record.changes.size
        toolTipText = SettingsSyncBundle.message("ui.toolwindow.undo.tooltip.text", numberOfChanges)
        ExpUiIcons.General.Delete
      }
      HistoryRecord.RecordPosition.MIDDLE, HistoryRecord.RecordPosition.BOTTOM -> {
        addTooltipTextFragment(TooltipTextFragment(SettingsSyncBundle.message("ui.toolwindow.reset.tooltip.text"), false, false))
        addNewLineToTooltip()
        addTooltipTextFragment(TooltipTextFragment(SettingsSyncBundle.message("ui.toolwindow.reset.tooltip.description"), true, true))
        toolTipText = buildTooltip()
        AllIcons.Diff.Revert
      }
      HistoryRecord.RecordPosition.SINGLE -> null
    }
  }
}