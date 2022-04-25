package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.util.gotoByName.GotoActionItemProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.util.Time.*

internal abstract class SearchEverywhereBaseActionFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(ActionSearchEverywhereContributor::class.java, TopHitSEContributor::class.java) {
  companion object {
    internal val IS_ENABLED = EventFields.Boolean("isEnabled")

    internal val ITEM_TYPE = EventFields.Enum<GotoActionModel.MatchedValueType>("type")
    internal val TYPE_WEIGHT = EventFields.Int("typeWeight")
    internal val IS_HIGH_PRIORITY = EventFields.Boolean("isHighPriority")

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
    return arrayListOf(
      IS_ENABLED, ITEM_TYPE, TYPE_WEIGHT, IS_HIGH_PRIORITY,
      USAGE, USAGE_SE, USAGE_TO_MAX, USAGE_TO_MAX_SE,
      TIME_SINCE_LAST_USAGE, TIME_SINCE_LAST_USAGE_SE,
      WAS_USED_IN_LAST_MINUTE, WAS_USED_IN_LAST_MINUTE_SE,
      WAS_USED_IN_LAST_HOUR, WAS_USED_IN_LAST_HOUR_SE,
      WAS_USED_IN_LAST_DAY, WAS_USED_IN_LAST_DAY_SE,
      WAS_USED_IN_LAST_MONTH, WAS_USED_IN_LAST_MONTH_SE,
    )
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): List<EventPair<*>> {
    val data = arrayListOf<EventPair<*>>()
    addIfTrue(data, IS_HIGH_PRIORITY, isHighPriority(elementPriority))

    // (element is GotoActionModel.MatchedValue) for actions and option provided by 'ActionSearchEverywhereContributor'
    // (element is OptionDescription || element is AnAction) for actions and option provided by 'TopHitSEContributor'
    if (element is GotoActionModel.MatchedValue) {
      data.add(ITEM_TYPE.with(element.type))
      data.add(TYPE_WEIGHT.with(element.valueTypeWeight))
    }

    val value = if (element is GotoActionModel.MatchedValue) element.value else element
    val actionText = GotoActionItemProvider.getActionText(value)
    actionText?.let {
      data.addAll(getNameMatchingFeatures(it, searchQuery))
    }
    return getFeatures(data, currentTime, value)
  }

  private fun isHighPriority(priority: Int): Boolean = priority >= 11001

  /**
   * [value] is either
   * [com.intellij.ide.ui.search.OptionDescription],
   * [com.intellij.execution.Executor.ActionWrapper]
   * or [com.intellij.openapi.actionSystem.AnAction]
   */
  abstract fun getFeatures(data: MutableList<EventPair<*>>, currentTime: Long, value: Any): List<EventPair<*>>

  internal fun addTimeAndUsageStatistics(data: MutableList<EventPair<*>>,
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

      addIfTrue(data, if (isSe) WAS_USED_IN_LAST_MINUTE_SE else WAS_USED_IN_LAST_MINUTE, timeSinceLastUsage <= MINUTE)
      addIfTrue(data, if (isSe) WAS_USED_IN_LAST_HOUR_SE else WAS_USED_IN_LAST_HOUR, timeSinceLastUsage <= HOUR)
      addIfTrue(data, if (isSe) WAS_USED_IN_LAST_DAY_SE else WAS_USED_IN_LAST_DAY, timeSinceLastUsage <= DAY)
      addIfTrue(data, if (isSe) WAS_USED_IN_LAST_MONTH_SE else WAS_USED_IN_LAST_MONTH, timeSinceLastUsage <= (4 * WEEK.toLong()))
    }
  }

  internal fun addIfTrue(result: MutableList<EventPair<*>>, key: BooleanEventField, value: Boolean) {
    if (value) {
      result.add(key.with(true))
    }
  }
}