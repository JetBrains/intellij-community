// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.local.ActionGlobalUsageInfo
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.actionSystem.EditorAction

internal class SearchEverywhereActionFeaturesProvider : SearchEverywhereBaseActionFeaturesProvider() {
  companion object {
    private const val IS_ACTION_DATA_KEY = "isAction"
    private const val IS_TOGGLE_ACTION_DATA_KEY = "isToggleAction"
    private const val IS_EDITOR_ACTION = "isEditorAction"
    private const val IS_SEARCH_ACTION = "isSearchAction"

    private const val MATCH_MODE_KEY = "matchMode"
    private const val TEXT_LENGTH_KEY = "textLength"
    private const val IS_GROUP_KEY = "isGroup"
    private const val GROUP_LENGTH_KEY = "groupLength"
    private const val HAS_ICON_KEY = "withIcon"
    private const val WEIGHT_KEY = "weight"
    private const val PLUGIN_TYPE = "pluginType"
    private const val PLUGIN_ID = "pluginId"

    private const val GLOBAL_USAGE_COUNT_KEY = "globalUsage"
    private const val GLOBAL_USAGE_TO_MAX_KEY = "globalUsageToMax"
    private const val USERS_RATIO_DATA_KEY = "usersRatio"
    private const val USAGES_PER_USER_RATIO_DATA_KEY = "usagesPerUserRatio"
  }

  override fun getFeatures(data: MutableMap<String, Any>, currentTime: Long, matchedValue: GotoActionModel.MatchedValue): Map<String, Any> {
    val actionWrapper = matchedValue.value as? GotoActionModel.ActionWrapper
    data[IS_ACTION_DATA_KEY] = actionWrapper != null

    if (actionWrapper == null) {
      // item is an option (OptionDescriptor)
      return data
    }

    data[MATCH_MODE_KEY] = actionWrapper.mode
    data[IS_GROUP_KEY] = actionWrapper.isGroupAction

    val action = actionWrapper.action
    addIfTrue(data, IS_EDITOR_ACTION, action is EditorAction)
    addIfTrue(data, IS_SEARCH_ACTION, action is SearchEverywhereBaseAction)
    addIfTrue(data, IS_TOGGLE_ACTION_DATA_KEY, action is ToggleAction)
    actionWrapper.actionText?.let {
      data[TEXT_LENGTH_KEY] = withUpperBound(it.length)
    }

    actionWrapper.groupName?.let {
      data[GROUP_LENGTH_KEY] = withUpperBound(it.length)
    }

    val presentation = if (actionWrapper.hasPresentation()) actionWrapper.presentation else action.templatePresentation
    data[HAS_ICON_KEY] = presentation.icon != null
    data[IS_ENABLED] = presentation.isEnabled
    data[WEIGHT_KEY] = presentation.weight

    data.putAll(getLocalUsageStatistics(action, currentTime))

    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    val globalSummary = service<ActionsGlobalSummaryManager>()

    val actionStats = globalSummary.getActionStatistics(actionId)
    val maxUsageCount = globalSummary.totalSummary.maxUsageCount
    data.putAll(getGlobalUsageStatistics(actionStats, maxUsageCount))

    val updatedActionStats = globalSummary.getUpdatedActionStatistics(actionId)
    val updatedMaxUsageCount = globalSummary.updatedTotalSummary.maxUsageCount
    val suffix = "V" + globalSummary.updatedStatisticsVersion
    data.putAll(getGlobalUsageStatistics(updatedActionStats, updatedMaxUsageCount, suffix))

    val pluginInfo = getPluginInfo(action.javaClass)
    if (pluginInfo.isSafeToReport()) {
      data[PLUGIN_TYPE] = pluginInfo.type
      pluginInfo.id?.let { data[PLUGIN_ID] = it }
    }
    return data
  }

  private fun getGlobalUsageStatistics(actionGlobalStatistics: ActionGlobalUsageInfo?, maxUsageCount: Long, suffix: String = "") : Map<String, Any> {
    val result = hashMapOf<String, Any>()
    actionGlobalStatistics?.let {
      result[GLOBAL_USAGE_COUNT_KEY + suffix] = it.usagesCount
      if (maxUsageCount != 0L) {
        result[GLOBAL_USAGE_TO_MAX_KEY + suffix] = roundDouble(it.usagesCount.toDouble() / maxUsageCount)
      }
      result[USERS_RATIO_DATA_KEY + suffix] = roundDouble(it.usersRatio)
      result[USAGES_PER_USER_RATIO_DATA_KEY + suffix] = roundDouble(it.usagesPerUserRatio)
    }
    return result
  }

  private fun getLocalUsageStatistics(action: AnAction,
                                      currentTime: Long): Map<String, Any> {
    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    val localSummary = service<ActionsLocalSummary>()
    val summary = localSummary.getActionStatsById(actionId) ?: return emptyMap()
    val totalStats = localSummary.getTotalStats()

    val result = hashMapOf<String, Any>()
    addTimeAndUsageStatistics(result, summary.usageCount, totalStats.maxUsageCount, currentTime, summary.lastUsedTimestamp, "")
    addTimeAndUsageStatistics(result,
      summary.usageFromSearchEverywhere, totalStats.maxUsageFromSearchEverywhere,
      currentTime, summary.lastUsedFromSearchEverywhere,
      "Se"
    )
    return result
  }
}