package com.intellij.searchEverywhereMl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.DumbAwareToggleAction

internal class ToggleSearchEverywhereMlSortingAction : DumbAwareToggleAction() {
  private val mlRankingTabs: List<SearchEverywhereTab.TabWithMlRanking>
    get() = SearchEverywhereTab.tabsWithLogging.filterIsInstance<SearchEverywhereTab.TabWithMlRanking>().filter {
      it.tabId != SearchEverywhereTab.All.tabId
    }

  override fun isSelected(e: AnActionEvent): Boolean =
    mlRankingTabs.all { AdvancedSettings.getBoolean(it.advancedSettingKey) }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    mlRankingTabs.forEach { AdvancedSettings.setBoolean(it.advancedSettingKey, state) }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
