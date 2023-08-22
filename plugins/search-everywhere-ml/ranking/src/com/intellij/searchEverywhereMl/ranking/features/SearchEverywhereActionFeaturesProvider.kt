// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.features

import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.internal.statistic.collectors.fus.PluginIdRuleValidator
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
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereGeneralActionFeaturesProvider.Companion.IS_ENABLED
import com.intellij.util.Time

internal class SearchEverywhereActionFeaturesProvider :
  SearchEverywhereElementFeaturesProvider(ActionSearchEverywhereContributor::class.java, TopHitSEContributor::class.java) {
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
    internal val PLUGIN_ID = EventFields.StringValidatedByCustomRule("pluginId", PluginIdRuleValidator::class.java)

    private val GLOBAL_STATISTICS_DEFAULT = GlobalStatisticsFields(ActionsGlobalSummaryManager.getDefaultStatisticsVersion())
    private val GLOBAL_STATISTICS_UPDATED = GlobalStatisticsFields(ActionsGlobalSummaryManager.getUpdatedStatisticsVersion())


    internal val USAGE = EventFields.Int("usage")
    internal val USAGE_SE = EventFields.Int("usageSe")
    internal val USAGE_TO_MAX = EventFields.Double("usageToMax")
    internal val USAGE_TO_MAX_SE = EventFields.Double("usageToMaxSe")

    internal val TIME_SINCE_LAST_USAGE = EventFields.Long("timeSinceLastUsage")
    internal val TIME_SINCE_LAST_USAGE_SE = EventFields.Long("timeSinceLastUsageSe")
    internal val WAS_USED_IN_LAST_MINUTE = EventFields.Boolean("wasUsedInLastMinute")
    internal val WAS_USED_IN_LAST_MINUTE_SE = EventFields.Boolean("wasUsedInLastMinuteSe")
    internal val WAS_USED_IN_LAST_HOUR = EventFields.Boolean("wasUsedInLastHour")
    internal val WAS_USED_IN_LAST_HOUR_SE = EventFields.Boolean("wasUsedInLastHourSe")
    internal val WAS_USED_IN_LAST_DAY = EventFields.Boolean("wasUsedInLastDay")
    internal val WAS_USED_IN_LAST_DAY_SE = EventFields.Boolean("wasUsedInLastDaySe")
    internal val WAS_USED_IN_LAST_MONTH = EventFields.Boolean("wasUsedInLastMonth")
    internal val WAS_USED_IN_LAST_MONTH_SE = EventFields.Boolean("wasUsedInLastMonthSe")
  }

  override fun getFeaturesDeclarations(): List<EventField<*>> {
    val fields = arrayListOf<EventField<*>>(
      IS_ACTION_DATA_KEY, IS_TOGGLE_ACTION_DATA_KEY, IS_EDITOR_ACTION, IS_SEARCH_ACTION,
      MATCH_MODE_KEY, TEXT_LENGTH_KEY, IS_GROUP_KEY, GROUP_LENGTH_KEY, HAS_ICON_KEY, WEIGHT_KEY,
      PLUGIN_TYPE, PLUGIN_ID,
      USAGE, USAGE_SE, USAGE_TO_MAX, USAGE_TO_MAX_SE,
      TIME_SINCE_LAST_USAGE, TIME_SINCE_LAST_USAGE_SE,
      WAS_USED_IN_LAST_MINUTE, WAS_USED_IN_LAST_MINUTE_SE,
      WAS_USED_IN_LAST_HOUR, WAS_USED_IN_LAST_HOUR_SE,
      WAS_USED_IN_LAST_DAY, WAS_USED_IN_LAST_DAY_SE,
      WAS_USED_IN_LAST_MONTH, WAS_USED_IN_LAST_MONTH_SE,
    )
    fields.addAll(GLOBAL_STATISTICS_DEFAULT.getFieldsDeclaration() + GLOBAL_STATISTICS_UPDATED.getFieldsDeclaration())

    return fields
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?): List<EventPair<*>> {
    val value = if (element is GotoActionModel.MatchedValue) element.value else element
    val action = getAnAction(value) ?: return emptyList()

    val data = arrayListOf<EventPair<*>>()
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
    data.addIfTrue(IS_EDITOR_ACTION, action is EditorAction)
    data.addIfTrue(IS_SEARCH_ACTION, action is SearchEverywhereBaseAction)
    data.addIfTrue(IS_TOGGLE_ACTION_DATA_KEY, action is ToggleAction)

    val presentation = if (value is ActionWrapper && value.hasPresentation()) value.presentation else action.templatePresentation
    data.add(HAS_ICON_KEY.with(presentation.icon != null))
    data.add(IS_ENABLED.with(presentation.isEnabled))
    data.add(WEIGHT_KEY.with(presentation.weight))

    data.addAll(getLocalUsageStatistics(action, currentTime))

    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    val globalSummary = service<ActionsGlobalSummaryManager>()

    val actionStats = globalSummary.getActionStatistics(actionId)
    val maxUsageCount = globalSummary.totalSummary.maxUsageCount()
    data.addAll(GLOBAL_STATISTICS_DEFAULT.getGlobalUsageStatistics(actionStats, maxUsageCount))

    val updatedActionStats = globalSummary.getUpdatedActionStatistics(actionId)
    val updatedMaxUsageCount = globalSummary.updatedTotalSummary.maxUsageCount()
    data.addAll(GLOBAL_STATISTICS_UPDATED.getGlobalUsageStatistics(updatedActionStats, updatedMaxUsageCount))


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

  private fun getLocalUsageStatistics(action: AnAction,
                                      currentTime: Long): List<EventPair<*>> {
    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    val localSummary = service<ActionsLocalSummary>()
    val summary = localSummary.getActionStatsById(actionId) ?: return emptyList()
    val totalStats = localSummary.getTotalStats()

    val result = arrayListOf<EventPair<*>>()
    addTimeAndUsageStatistics(result, summary.usageCount, totalStats.maxUsageCount, currentTime, summary.lastUsedTimestamp, false)
    addTimeAndUsageStatistics(
      result,
      summary.usageFromSearchEverywhere, totalStats.maxUsageFromSearchEverywhere,
      currentTime, summary.lastUsedFromSearchEverywhere,
      true
    )
    return result
  }

  private fun addTimeAndUsageStatistics(data: MutableList<EventPair<*>>,
                                        usage: Int, maxUsage: Int,
                                        time: Long, lastUsedTime: Long,
                                        isSe: Boolean) {
    addUsageStatistics(data, usage, maxUsage, isSe)
    addLastTimeUsedStatistics(data, time, lastUsedTime, isSe)
  }

  private fun addUsageStatistics(data: MutableList<EventPair<*>>, usage: Int, maxUsage: Int, isSe: Boolean) {
    if (usage > 0) {
      val usageEventId = if (isSe) USAGE_SE else USAGE
      data.add(usageEventId.with(usage))
      if (maxUsage != 0) {
        val usageToMaxEventId = if (isSe) USAGE_TO_MAX_SE else USAGE_TO_MAX
        data.add(usageToMaxEventId.with(roundDouble(usage.toDouble() / maxUsage)))
      }
    }
  }

  private fun addLastTimeUsedStatistics(data: MutableList<EventPair<*>>, time: Long, lastUsedTime: Long, isSe: Boolean) {
    if (lastUsedTime > 0) {
      val timeSinceLastUsage = time - lastUsedTime
      val timeSinceLastUsageEvent = if (isSe) TIME_SINCE_LAST_USAGE_SE else TIME_SINCE_LAST_USAGE
      data.add(timeSinceLastUsageEvent.with(timeSinceLastUsage))

      data.addIfTrue(if (isSe) WAS_USED_IN_LAST_MINUTE_SE else WAS_USED_IN_LAST_MINUTE, timeSinceLastUsage <= Time.MINUTE)
      data.addIfTrue(if (isSe) WAS_USED_IN_LAST_HOUR_SE else WAS_USED_IN_LAST_HOUR, timeSinceLastUsage <= Time.HOUR)
      data.addIfTrue(if (isSe) WAS_USED_IN_LAST_DAY_SE else WAS_USED_IN_LAST_DAY, timeSinceLastUsage <= Time.DAY)
      data.addIfTrue(if (isSe) WAS_USED_IN_LAST_MONTH_SE else WAS_USED_IN_LAST_MONTH, timeSinceLastUsage <= (4 * Time.WEEK.toLong()))
    }
  }

  internal class GlobalStatisticsFields(version: Int) {
    private val globalUsage = EventFields.Long("globalUsageV$version")
    private val globalUsageToMax = EventFields.Double("globalUsageToMaxV$version")
    private val usersRatio = EventFields.Double("usersRatioV$version")
    private val usagesPerUserRatio = EventFields.Double("usagesPerUserRatioV$version")

    fun getFieldsDeclaration(): List<EventField<*>> = arrayListOf(globalUsage, globalUsageToMax, usersRatio, usagesPerUserRatio)

    fun getGlobalUsageStatistics(actionGlobalStatistics: ActionGlobalUsageInfo?, maxUsageCount: Long): List<EventPair<*>> {
      val result = arrayListOf<EventPair<*>>()
      actionGlobalStatistics?.let {
        result.add(globalUsage.with(it.usagesCount))
        if (maxUsageCount != 0L) {
          result.add(globalUsageToMax.with(roundDouble(it.usagesCount.toDouble() / maxUsageCount)))
        }
        result.add(usersRatio.with(roundDouble(it.usersRatio)))
        result.add(usagesPerUserRatio.with(roundDouble(it.usagesPerUserRatio)))
      }
      return result
    }
  }
}