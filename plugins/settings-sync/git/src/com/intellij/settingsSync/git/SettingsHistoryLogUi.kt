package com.intellij.settingsSync.git

import com.intellij.vcs.log.VcsLogFilterUi
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiPropertiesImpl
import com.intellij.vcs.log.ui.AbstractVcsLogUi
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import javax.swing.JComponent

class SettingsHistoryLogUi(logId: String,
                           logData: VcsLogData,
                           refresher: VisiblePackRefresher,
                           colorManager: VcsLogColorManager) : AbstractVcsLogUi(logId,
                                                                             logData,
                                                                             colorManager,
                                                                             refresher) {
  private val uiProperties = SettingsHistoryUiProperties<VcsLogUiPropertiesImpl.State>()
  private val settingsHistoryPanel = SettingsHistoryPanel(this, logData, refresher)
  private val filterUi = VcsLogFilterUi { VcsLogFilterObject.EMPTY_COLLECTION }

  override fun getFilterUi(): VcsLogFilterUi {
    return filterUi
  }

  override fun getTable(): VcsLogGraphTable {
    return settingsHistoryPanel.getGraphTable()
  }

  override fun getMainComponent(): JComponent {
    return settingsHistoryPanel
  }

  override fun getProperties(): VcsLogUiProperties {
    return uiProperties
  }

  override fun onVisiblePackUpdated(permGraphChanged: Boolean) {}
}