package com.intellij.settingsSync.git

import com.intellij.settingsSync.git.table.SettingsHistoryTable
import com.intellij.settingsSync.git.table.SettingsHistoryTableModel
import com.intellij.ui.components.JBScrollPane
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.AbstractVcsLogUi
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.visible.VisiblePackRefresher
import java.awt.BorderLayout
import javax.swing.JPanel

internal class SettingsHistoryPanel(logUi: AbstractVcsLogUi,
                                    logData: VcsLogData,
                                    refresher: VisiblePackRefresher) : JPanel() {
  private val graphTable = VcsLogGraphTable(logUi.id, logData, logUi.properties, logUi.colorManager, logUi::requestMore, logUi)

  init {
    layout = BorderLayout()

    val tableModel = SettingsHistoryTableModel(logData, refresher)
    val settingsHistoryTable = SettingsHistoryTable(tableModel, logData.project)
    tableModel.bindTable(settingsHistoryTable)

    settingsHistoryTable.tableHeader.setUI(null)
    add(JBScrollPane(settingsHistoryTable), BorderLayout.CENTER)
  }

  fun getGraphTable(): VcsLogGraphTable {
    return graphTable
  }
}
