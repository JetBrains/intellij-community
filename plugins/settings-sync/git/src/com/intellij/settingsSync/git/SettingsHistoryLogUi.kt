package com.intellij.settingsSync.git

import com.intellij.vcs.log.VcsLogFilterUi
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl
import com.intellij.vcs.log.ui.VcsLogUiBase
import com.intellij.vcs.log.ui.table.VcsLogCommitList
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import javax.swing.JComponent

class SettingsHistoryLogUi(logId: String, logData: VcsLogData, refresher: VisiblePackRefresher) : VcsLogUiBase(logId, logData, refresher) {
  private val uiProperties = SettingsHistoryUiProperties()
  private val settingsHistoryPanel = SettingsHistoryPanel(logData, refresher)
  private val filterUi = VcsLogFilterUi { VcsLogFilterObject.EMPTY_COLLECTION }

  override fun getTable(): VcsLogCommitList = settingsHistoryPanel.settingsHistoryTable
  override fun getMainComponent(): JComponent = settingsHistoryPanel
  override fun getProperties(): VcsLogUiProperties = uiProperties
  override fun getFilterUi(): VcsLogFilterUi = filterUi
  override fun getDataPack(): VisiblePack = settingsHistoryPanel.visiblePack
}