// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
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
    internal val IS_ACTION_DATA_KEY = EventFields.Boolean("isAction")
    internal val IS_TOGGLE_ACTION_DATA_KEY = EventFields.Boolean("isToggleAction")
    internal val IS_EDITOR_ACTION = EventFields.Boolean("isEditorAction")
    internal val IS_SEARCH_ACTION = EventFields.Boolean("isSearchAction")

    internal val MATCH_MODE_KEY = EventFields.Enum<MatchMode>("matchMode")
    internal val TEXT_LENGTH_KEY = EventFields.Int("textLength")
    internal val IS_GROUP_KEY = EventFields.Boolean("isGroup")
    internal val GROUP_LENGTH_KEY = EventFields.Int("groupLength")
    internal val HAS_ICON_KEY = EventFields.Boolean("withIcon")
    internal val WEIGHT_KEY = EventFields.Double("weight")
    internal val PLUGIN_TYPE = EventFields.StringValidatedByEnum("pluginType", "plugin_type")
    internal val PLUGIN_ID = EventFields.StringValidatedByCustomRule("pluginId", "plugin")

    private val version = "V" + ActionsGlobalSummaryManager.getUpdatedStatisticsVersion()
    internal val GLOBAL_USAGE_COUNT_KEY = EventFields.Long("globalUsage")
    internal val GLOBAL_USAGE_COUNT_KEY_VERSIONED = EventFields.Long("globalUsage$version")
    internal val GLOBAL_USAGE_TO_MAX_KEY = EventFields.Double("globalUsageToMax")
    internal val GLOBAL_USAGE_TO_MAX_KEY_VERSIONED = EventFields.Double("globalUsageToMax$version")
    internal val USERS_RATIO_DATA_KEY = EventFields.Double("usersRatio")
    internal val USERS_RATIO_DATA_KEY_VERSIONED = EventFields.Double("usersRatio$version")
    internal val USAGES_PER_USER_RATIO_DATA_KEY = EventFields.Double("usagesPerUserRatio")
    internal val USAGES_PER_USER_RATIO_DATA_KEY_VERSIONED = EventFields.Double("usagesPerUserRatio$version")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    val features = arrayListOf<EventField<*>>(
      IS_ACTION_DATA_KEY, IS_TOGGLE_ACTION_DATA_KEY, IS_EDITOR_ACTION, IS_SEARCH_ACTION,
      MATCH_MODE_KEY, TEXT_LENGTH_KEY, IS_GROUP_KEY, GROUP_LENGTH_KEY, HAS_ICON_KEY, WEIGHT_KEY,
      PLUGIN_TYPE, PLUGIN_ID, GLOBAL_USAGE_COUNT_KEY, GLOBAL_USAGE_COUNT_KEY_VERSIONED, GLOBAL_USAGE_TO_MAX_KEY,
      GLOBAL_USAGE_TO_MAX_KEY_VERSIONED, USERS_RATIO_DATA_KEY, USERS_RATIO_DATA_KEY_VERSIONED,
      USAGES_PER_USER_RATIO_DATA_KEY, USAGES_PER_USER_RATIO_DATA_KEY_VERSIONED,
    )
    features.addAll(super.getFeaturesDeclarations())
    return features
  }

  override fun getFeatures(data: MutableList<EventPair<*>>, currentTime: Long, value: Any): List<EventPair<*>> {
    // 'action' is null if item is an option (OptionDescriptor)
    val action = getAnAction(value) ?: return data

    data.add(IS_ACTION_DATA_KEY.with(true))
    if (value is ActionWrapper) {
      data.add(MATCH_MODE_KEY.with(value.mode))
      data.add(IS_GROUP_KEY.with(value.isGroupAction))

      value.actionText?.let {
        data.add(TEXT_LENGTH_KEY.with(withUpperBound(it.length)))
      }

      value.groupName?.let {
        data.add(GROUP_LENGTH_KEY.with(withUpperBound(it.length)))
      }
    }
    addIfTrue(data, IS_EDITOR_ACTION, action is EditorAction)
    addIfTrue(data, IS_SEARCH_ACTION, action is SearchEverywhereBaseAction)
    addIfTrue(data, IS_TOGGLE_ACTION_DATA_KEY, action is ToggleAction)

    val presentation = if (value is ActionWrapper && value.hasPresentation()) value.presentation else action.templatePresentation
    data.add(HAS_ICON_KEY.with(presentation.icon != null))
    data.add(IS_ENABLED.with(presentation.isEnabled))
    data.add(WEIGHT_KEY.with(presentation.weight))

    data.addAll(getLocalUsageStatistics(action, currentTime))

    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    val globalSummary = service<ActionsGlobalSummaryManager>()

    val actionStats = globalSummary.getActionStatistics(actionId)
    val maxUsageCount = globalSummary.totalSummary.maxUsageCount
    data.addAll(getGlobalUsageStatistics(actionStats, maxUsageCount, false))

    val updatedActionStats = globalSummary.getUpdatedActionStatistics(actionId)
    val updatedMaxUsageCount = globalSummary.updatedTotalSummary.maxUsageCount
    data.addAll(getGlobalUsageStatistics(updatedActionStats, updatedMaxUsageCount, true))

    val pluginInfo = getPluginInfo(action.javaClass)
    if (pluginInfo.isSafeToReport()) {
      data.add(PLUGIN_TYPE.with(pluginInfo.type.name))
      pluginInfo.id?.let { data.add(PLUGIN_ID.with(it)) }
    }
    return data
  }

  private fun getAnAction(value: Any): AnAction? {
    if (value is ActionWrapper) {
      return value.action
    }
    else if (value is AnAction) {
      return value
    }
    return null
  }

  private fun getGlobalUsageStatistics(actionGlobalStatistics: ActionGlobalUsageInfo?, maxUsageCount: Long, isVersioned: Boolean) : List<EventPair<*>> {
    val result = arrayListOf<EventPair<*>>()
    actionGlobalStatistics?.let {
      val globalUsageCountKeyEventId = if (isVersioned) GLOBAL_USAGE_COUNT_KEY_VERSIONED else GLOBAL_USAGE_COUNT_KEY
      result.add(globalUsageCountKeyEventId.with(it.usagesCount))
      if (maxUsageCount != 0L) {
        val globalUsageToMaxKeyEventId = if (isVersioned) GLOBAL_USAGE_TO_MAX_KEY_VERSIONED else GLOBAL_USAGE_TO_MAX_KEY
        result.add(globalUsageToMaxKeyEventId.with(roundDouble(it.usagesCount.toDouble() / maxUsageCount)))
      }
      val usersRatioDataKeyEventId = if (isVersioned) USERS_RATIO_DATA_KEY_VERSIONED else USERS_RATIO_DATA_KEY
      result.add(usersRatioDataKeyEventId.with(roundDouble(it.usersRatio)))
      val usagesPerUserRatioDataKeyEventId = if (isVersioned) USAGES_PER_USER_RATIO_DATA_KEY_VERSIONED else USAGES_PER_USER_RATIO_DATA_KEY
      result.add(usagesPerUserRatioDataKeyEventId.with(roundDouble(it.usagesPerUserRatio)))
    }
    return result
  }

  private fun getLocalUsageStatistics(action: AnAction,
                                      currentTime: Long): List<EventPair<*>> {
    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    val localSummary = service<ActionsLocalSummary>()
    val summary = localSummary.getActionStatsById(actionId) ?: return emptyList()
    val totalStats = localSummary.getTotalStats()

    val result = arrayListOf<EventPair<*>>()
    addTimeAndUsageStatistics(result, summary.usageCount, totalStats.maxUsageCount, currentTime, summary.lastUsedTimestamp, false)
    addTimeAndUsageStatistics(result,
      summary.usageFromSearchEverywhere, totalStats.maxUsageFromSearchEverywhere,
      currentTime, summary.lastUsedFromSearchEverywhere,
      true
    )
    return result
  }
}