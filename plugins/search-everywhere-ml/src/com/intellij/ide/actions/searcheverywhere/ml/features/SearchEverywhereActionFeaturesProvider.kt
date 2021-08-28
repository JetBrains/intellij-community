// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.TOTAL_SYMBOLS_AMOUNT_DATA_KEY
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.util.Time.*

internal class SearchEverywhereActionFeaturesProvider : SearchEverywhereElementFeaturesProvider() {
  companion object {
    private const val ITEM_TYPE = "type"
    private const val IS_ACTION_DATA_KEY = "isAction"
    private const val IS_TOGGLE_ACTION_DATA_KEY = "isToggleAction"
    private const val PRIORITY_DATA_KEY = "priority"
    private const val MATCH_MODE_KEY = "matchMode"
    private const val TEXT_LENGTH_KEY = "textLength"
    private const val IS_GROUP_KEY = "isGroup"
    private const val GROUP_LENGTH_KEY = "groupLength"
    private const val HAS_ICON_KEY = "withIcon"
    private const val IS_ENABLED_KEY = "isEnabled"
    private const val WEIGHT_KEY = "weight"

    private const val TIME_SINCE_LAST_USAGE_DATA_KEY = "timeSinceLastUsage"
    private const val WAS_USED_IN_LAST_MINUTE_DATA_KEY = "wasUsedInLastMinute"
    private const val WAS_USED_IN_LAST_HOUR_DATA_KEY = "wasUsedInLastHour"
    private const val WAS_USED_IN_LAST_DAY_DATA_KEY = "wasUsedInLastDay"
    private const val WAS_USED_IN_LAST_MONTH_DATA_KEY = "wasUsedInLastMonth"

    private const val LOCAL_USAGE_COUNT_DATA_KEY = "usage"
    private const val GLOBAL_USAGE_COUNT_KEY = "globalUsage"
    private const val USERS_RATIO_DATA_KEY = "usersRatio"
    private const val USAGES_PER_USER_RATIO_DATA_KEY = "usagesPerUserRatio"
  }

  override fun isElementSupported(element: Any): Boolean {
    return element is GotoActionModel.MatchedValue
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  queryLength: Int,
                                  elementPriority: Int,
                                  cache: Any?): Map<String, Any> {
    if (element !is GotoActionModel.MatchedValue) {
      // not an action/option
      return emptyMap()
    }
    return getActionsOrOptionsFeatures(element.matchingDegree, currentTime, element, queryLength)
  }

  private fun getActionsOrOptionsFeatures(priority: Int,
                                          currentTime: Long,
                                          matchedValue: GotoActionModel.MatchedValue,
                                          queryLength: Int): Map<String, Any> {
    val wrapper = matchedValue.value as? GotoActionModel.ActionWrapper
    val data = hashMapOf<String, Any>(
      TOTAL_SYMBOLS_AMOUNT_DATA_KEY to queryLength,
      ITEM_TYPE to matchedValue.type,
      IS_ACTION_DATA_KEY to (wrapper != null),
      PRIORITY_DATA_KEY to priority
    )

    if (wrapper == null) {
      // item is an option (OptionDescriptor)
      return data
    }

    data[MATCH_MODE_KEY] = wrapper.mode
    data[IS_GROUP_KEY] = wrapper.isGroupAction
    val action = wrapper.action
    data[IS_TOGGLE_ACTION_DATA_KEY] = action is ToggleAction
    wrapper.actionText?.let {
      data[TEXT_LENGTH_KEY] = withUpperBound(it.length)
    }

    wrapper.groupName?.let {
      data[GROUP_LENGTH_KEY] = withUpperBound(it.length)
    }

    val presentation = if (wrapper.hasPresentation()) wrapper.presentation else action.templatePresentation
    data[HAS_ICON_KEY] = presentation.icon != null
    data[IS_ENABLED_KEY] = presentation.isEnabled
    data[WEIGHT_KEY] = presentation.weight

    data.putAll(getLocalUsageStatistics(action, currentTime))

    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    val globalSummary = service<ActionsGlobalSummaryManager>()
    globalSummary.getActionStatistics(actionId)?.let {
      data[GLOBAL_USAGE_COUNT_KEY] = it.usagesCount
      data[USERS_RATIO_DATA_KEY] = roundDouble(it.usersRatio)
      data[USAGES_PER_USER_RATIO_DATA_KEY] = roundDouble(it.usagesPerUserRatio)
    }
    return data
  }

  private fun getLocalUsageStatistics(action: AnAction,
                                      currentTime: Long): Map<String, Any> {
    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    val localSummary = service<ActionsLocalSummary>()
    val actionSummary = localSummary.getActionStatsById(actionId) ?: return emptyMap()

    val timeSinceLastUsage = currentTime - actionSummary.lastUsedTimestamp

    return hashMapOf(
      LOCAL_USAGE_COUNT_DATA_KEY to actionSummary.usageCount,

      TIME_SINCE_LAST_USAGE_DATA_KEY to timeSinceLastUsage,
      WAS_USED_IN_LAST_MINUTE_DATA_KEY to (timeSinceLastUsage <= MINUTE),
      WAS_USED_IN_LAST_HOUR_DATA_KEY to (timeSinceLastUsage <= HOUR),
      WAS_USED_IN_LAST_DAY_DATA_KEY to (timeSinceLastUsage <= DAY),
      WAS_USED_IN_LAST_MONTH_DATA_KEY to (timeSinceLastUsage <= (4 * WEEK.toLong()))
    )
  }
}