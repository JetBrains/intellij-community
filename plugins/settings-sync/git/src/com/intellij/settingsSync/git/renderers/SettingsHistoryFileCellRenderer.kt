package com.intellij.settingsSync.git.renderers

import com.intellij.settingsSync.git.table.FileRow
import com.intellij.settingsSync.git.table.SettingsHistoryTable
import com.intellij.settingsSync.git.table.SettingsHistoryTableRow
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI

internal class SettingsHistoryFileCellRenderer : SettingsHistoryCellRenderer() {
  override fun customizeHistoryCellRenderer(table: SettingsHistoryTable,
                                            row: SettingsHistoryTableRow,
                                            selected: Boolean,
                                            hasFocus: Boolean,
                                            rowIndex: Int) {
    row as FileRow

    val textAttributes: SimpleTextAttributes
    if (isGreyedOut(table, rowIndex)) {
      iconOpacity = 0.6f
      textAttributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
    }
    else {
      textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
    }
    icon = createLabelIcon(IconUtil.getIcon(row.virtualFile, 0, null), 16)
    ipad = JBUI.insetsLeft(35)
    iconTextGap = 4
    appendWithClipping(row.virtualFile.name, textAttributes, DefaultFragmentTextClipper.INSTANCE)
  }
}