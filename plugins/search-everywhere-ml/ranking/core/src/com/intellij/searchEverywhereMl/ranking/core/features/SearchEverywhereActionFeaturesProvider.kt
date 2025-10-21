// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper
import com.intellij.ide.util.gotoByName.MatchMode
import com.intellij.internal.statistic.collectors.fus.PluginIdRuleValidator
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.GLOBAL_STATISTICS_DEFAULT
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.GLOBAL_STATISTICS_UPDATED
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.GROUP_LENGTH_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.HAS_ICON_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.IS_ACTION_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.IS_EDITOR_ACTION
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.IS_GROUP_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.IS_SEARCH_ACTION
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.IS_TOGGLE_ACTION_DATA_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.MATCH_MODE_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.PLUGIN_ID
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.PLUGIN_TYPE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.TEXT_LENGTH_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.TIME_SINCE_LAST_USAGE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.TIME_SINCE_LAST_USAGE_SE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.USAGE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.USAGE_SE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.USAGE_TO_MAX
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.USAGE_TO_MAX_SE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.WAS_USED_IN_LAST_DAY
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.WAS_USED_IN_LAST_DAY_SE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.WAS_USED_IN_LAST_HOUR
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.WAS_USED_IN_LAST_HOUR_SE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.WAS_USED_IN_LAST_MINUTE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.WAS_USED_IN_LAST_MINUTE_SE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.WAS_USED_IN_LAST_MONTH
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereActionFeaturesProvider.Fields.WAS_USED_IN_LAST_MONTH_SE
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereGeneralActionFeaturesProvider.Fields.IS_ENABLED
import com.intellij.util.Time

internal class SearchEverywhereActionFeaturesProvider :
  SearchEverywhereElementFeaturesProvider(ActionSearchEverywhereContributor::class.java, TopHitSEContributor::class.java) {
  object Fields {
    internal val IS_ACTION_DATA_KEY = EventFields.Boolean("isAction")
    internal val IS_TOGGLE_ACTION_DATA_KEY = EventFields.Boolean("isToggleAction")
    internal val IS_EDITOR_ACTION = EventFields.Boolean("isEditorAction")
    internal val IS_SEARCH_ACTION = EventFields.Boolean("isSearchAction")

    internal val MATCH_MODE_KEY = EventFields.Enum<MatchMode>("matchMode")
    internal val TEXT_LENGTH_KEY = EventFields.Int("textLength")
    internal val IS_GROUP_KEY = EventFields.Boolean("isGroup")
    internal val GROUP_LENGTH_KEY = EventFields.Int("groupLength")
    internal val HAS_ICON_KEY = EventFields.Boolean("withIcon")
    internal val PLUGIN_TYPE = EventFields.StringValidatedByEnum("pluginType", "plugin_type")
    internal val PLUGIN_ID = EventFields.StringValidatedByCustomRule("pluginId", PluginIdRuleValidator::class.java)

    internal val GLOBAL_STATISTICS_DEFAULT = ActionsGlobalStatisticsFields(ActionsGlobalSummaryManager.STATISTICS_VERSION)
    internal val GLOBAL_STATISTICS_UPDATED = ActionsGlobalStatisticsFields(ActionsGlobalSummaryManager.UPDATED_STATISTICS_VERSION)


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
    return buildList {
      addAll(listOf(
        IS_ACTION_DATA_KEY, IS_TOGGLE_ACTION_DATA_KEY, IS_EDITOR_ACTION, IS_SEARCH_ACTION,
        MATCH_MODE_KEY, TEXT_LENGTH_KEY, IS_GROUP_KEY, GROUP_LENGTH_KEY, HAS_ICON_KEY,
        PLUGIN_TYPE, PLUGIN_ID,
        USAGE, USAGE_SE, USAGE_TO_MAX, USAGE_TO_MAX_SE,
        TIME_SINCE_LAST_USAGE, TIME_SINCE_LAST_USAGE_SE,
        WAS_USED_IN_LAST_MINUTE, WAS_USED_IN_LAST_MINUTE_SE,
        WAS_USED_IN_LAST_HOUR, WAS_USED_IN_LAST_HOUR_SE,
        WAS_USED_IN_LAST_DAY, WAS_USED_IN_LAST_DAY_SE,
        WAS_USED_IN_LAST_MONTH, WAS_USED_IN_LAST_MONTH_SE,
      ))
      addAll(GLOBAL_STATISTICS_DEFAULT.getFieldsDeclaration())
      addAll(GLOBAL_STATISTICS_UPDATED.getFieldsDeclaration())
    }

  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: FeaturesProviderCache?,
                                  correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    val value = if (element is GotoActionModel.MatchedValue) element.value else element
    val action = getAnAction(value) ?: return emptyList()

    return buildList {
      add(IS_ACTION_DATA_KEY.with(true))
      if (value is ActionWrapper) {
        add(MATCH_MODE_KEY.with(value.mode))
        add(IS_GROUP_KEY.with(value.isGroupAction))

        value.actionText?.let {
          add(TEXT_LENGTH_KEY.with(withUpperBound(it.length)))
        }

        value.groupName?.let {
          add(GROUP_LENGTH_KEY.with(withUpperBound(it.length)))
        }
      }
      addIfTrue(IS_EDITOR_ACTION, action is EditorAction)
      addIfTrue(IS_SEARCH_ACTION, action is SearchEverywhereBaseAction)
      addIfTrue(IS_TOGGLE_ACTION_DATA_KEY, action is ToggleAction)

      val presentation = (value as? ActionWrapper)?.presentation ?: action.templatePresentation
      add(HAS_ICON_KEY.with(presentation.icon != null))
      add(IS_ENABLED.with(presentation.isEnabled))

      addAll(getLocalUsageStatistics(action, currentTime))

      val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
      val globalSummary = ActionsGlobalSummaryManager.getInstance()

      val actionStats = globalSummary.getStatistics(actionId)
      val maxUsageCount = globalSummary.eventCountRange.maxEventCount
      addAll(GLOBAL_STATISTICS_DEFAULT.getEventGlobalStatistics(actionStats, maxUsageCount))

      val updatedActionStats = globalSummary.getUpdatedStatistics(actionId)
      val updatedMaxUsageCount = globalSummary.updatedEventCountRange.maxEventCount
      addAll(GLOBAL_STATISTICS_UPDATED.getEventGlobalStatistics(updatedActionStats, updatedMaxUsageCount))


      val pluginInfo = getPluginInfo(action.javaClass)
      if (pluginInfo.isSafeToReport()) {
        add(PLUGIN_TYPE.with(pluginInfo.type.name))
        pluginInfo.id?.let {
          add(PLUGIN_ID.with(it))
        }
      }
    }
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

    return buildList {
      addAll(getTimeAndUsageStatistics(summary.usageCount, totalStats.maxUsageCount, currentTime, summary.lastUsedTimestamp, false))
      addAll(getTimeAndUsageStatistics(
        summary.usageFromSearchEverywhere,
        totalStats.maxUsageFromSearchEverywhere,
        currentTime,
        summary.lastUsedFromSearchEverywhere,
        true
      ))
    }
  }

  private fun getTimeAndUsageStatistics(usage: Int, maxUsage: Int,
                                        time: Long, lastUsedTime: Long,
                                        isSe: Boolean): List<EventPair<*>> {
    return buildList {
      addAll(getUsageStatistics(usage, maxUsage, isSe))
      addAll(getLastTimeUsadStatistics(time, lastUsedTime, isSe))
    }
  }

  private fun getUsageStatistics(usage: Int, maxUsage: Int, isSe: Boolean): List<EventPair<*>> {
    if (usage <= 0) return emptyList()

    val usageEventId = if (isSe) USAGE_SE else USAGE
    val usageToMaxEventId = if (isSe) USAGE_TO_MAX_SE else USAGE_TO_MAX

    return buildList {
      add(usageEventId.with(usage))
      if (maxUsage != 0) {
        add(usageToMaxEventId.with(roundDouble(usage.toDouble() / maxUsage)))
      }
    }
  }

  private fun getLastTimeUsadStatistics(time: Long, lastUsedTime: Long, isSe: Boolean): List<EventPair<*>> {
    if (lastUsedTime <= 0) return emptyList()

    val timeSinceLastUsage = time - lastUsedTime
    val timeSinceLastUsageEvent = if (isSe) TIME_SINCE_LAST_USAGE_SE else TIME_SINCE_LAST_USAGE

    return buildList {
      add(timeSinceLastUsageEvent.with(timeSinceLastUsage))

      addIfTrue(if (isSe) WAS_USED_IN_LAST_MINUTE_SE else WAS_USED_IN_LAST_MINUTE, timeSinceLastUsage <= Time.MINUTE)
      addIfTrue(if (isSe) WAS_USED_IN_LAST_HOUR_SE else WAS_USED_IN_LAST_HOUR, timeSinceLastUsage <= Time.HOUR)
      addIfTrue(if (isSe) WAS_USED_IN_LAST_DAY_SE else WAS_USED_IN_LAST_DAY, timeSinceLastUsage <= Time.DAY)
      addIfTrue(if (isSe) WAS_USED_IN_LAST_MONTH_SE else WAS_USED_IN_LAST_MONTH, timeSinceLastUsage <= (4 * Time.WEEK.toLong()))
    }
  }
}