package com.intellij.settingsSync.git

import com.intellij.openapi.project.Project
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.visible.*
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

class SettingsHistoryLogUiFactory(private val colorManager: VcsLogColorManager) : VcsLogManager.VcsLogUiFactory<SettingsHistoryLogUi> {
  override fun createLogUi(project: Project, logData: VcsLogData): SettingsHistoryLogUi {
    val logId = "Settings Sync History"

    val filters = VcsLogFilterObject.EMPTY_COLLECTION
    val filterer = VcsLogFiltererImpl(logData)

    val refresher = VisiblePackRefresherImpl(project, logData, filters, PermanentGraph.SortType.Normal, filterer, logId)
    return SettingsHistoryLogUi(logId, logData, refresher, colorManager)
  }
}