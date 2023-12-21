package com.intellij.settingsSync.git

import com.intellij.settingsSync.git.table.SettingsHistoryTable
import com.intellij.settingsSync.git.table.SettingsHistoryTableModel
import com.intellij.ui.components.JBScrollPane
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.visible.VisiblePackRefresher
import java.awt.BorderLayout
import javax.swing.JPanel

internal class SettingsHistoryPanel(logData: VcsLogData, refresher: VisiblePackRefresher) : JPanel() {
  private val tableModel = SettingsHistoryTableModel(logData, refresher)
  internal val settingsHistoryTable = SettingsHistoryTable(tableModel, logData.project)
  internal val visiblePack get() = tableModel.visiblePack

  init {
    layout = BorderLayout()

    tableModel.bindTable(settingsHistoryTable)

    settingsHistoryTable.tableHeader.setUI(null)
    add(JBScrollPane(settingsHistoryTable), BorderLayout.CENTER)
  }
}
