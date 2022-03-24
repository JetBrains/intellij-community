package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.ide.util.gotoByName.GotoActionItemProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.util.Time.*

internal abstract class SearchEverywhereBaseActionFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(ActionSearchEverywhereContributor::class.java) {
  companion object {
    internal const val IS_ENABLED = "isEnabled"

    private const val ITEM_TYPE = "type"
    private const val TYPE_WEIGHT = "typeWeight"
    private const val PRIORITY = "priority"
    private const val IS_HIGH_PRIORITY = "isHighPriority"

    private const val USAGE = "usage"
    private const val USAGE_TO_MAX = "usageToMax"

    private const val TIME_SINCE_LAST_USAGE = "timeSinceLastUsage"
    private const val WAS_USED_IN_LAST_MINUTE = "wasUsedInLastMinute"
    private const val WAS_USED_IN_LAST_HOUR = "wasUsedInLastHour"
    private const val WAS_USED_IN_LAST_DAY = "wasUsedInLastDay"
    private const val WAS_USED_IN_LAST_MONTH = "wasUsedInLastMonth"
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  searchQuery: String,
                                  elementPriority: Int,
                                  cache: Any?): Map<String, Any> {
    if (element !is GotoActionModel.MatchedValue) {
      // not an action/option
      return emptyMap()
    }

    val priority = element.matchingDegree
    val data: MutableMap<String, Any> = hashMapOf(
      SearchEverywhereUsageTriggerCollector.TOTAL_SYMBOLS_AMOUNT_DATA_KEY to searchQuery.length,
      ITEM_TYPE to element.type,
      TYPE_WEIGHT to element.valueTypeWeight,
      PRIORITY to priority
    )
    addIfTrue(data, IS_HIGH_PRIORITY, isHighPriority(priority))

    val actionText = GotoActionItemProvider.getActionText(element.value)
    actionText?.let {
      data.putAll(getNameMatchingFeatures(it, searchQuery))
    }
    return getFeatures(data, currentTime, element)
  }

  private fun isHighPriority(priority: Int): Boolean = priority >= 11001

  abstract fun getFeatures(data: MutableMap<String, Any>, currentTime: Long, matchedValue: GotoActionModel.MatchedValue): Map<String, Any>

  internal fun addTimeAndUsageStatistics(data: MutableMap<String, Any>,
                                         usage: Int, maxUsage: Int,
                                         time: Long, lastUsedTime: Long,
                                         suffix: String) {
    addUsageStatistics(data, usage, maxUsage, suffix)
    addLastTimeUsedStatistics(data, time, lastUsedTime, suffix)
  }

  private fun addUsageStatistics(data: MutableMap<String, Any>, usage: Int, maxUsage: Int, suffix: String) {
    if (usage > 0) {
      data[USAGE + suffix] = usage
      if (maxUsage != 0) {
        data[USAGE_TO_MAX + suffix] = roundDouble(usage.toDouble() / maxUsage)
      }
    }
  }

  private fun addLastTimeUsedStatistics(data: MutableMap<String, Any>, time: Long, lastUsedTime: Long, suffix: String) {
    if (lastUsedTime > 0) {
      val timeSinceLastUsage = time - lastUsedTime
      data[TIME_SINCE_LAST_USAGE + suffix] = timeSinceLastUsage

      addIfTrue(data, WAS_USED_IN_LAST_MINUTE + suffix, timeSinceLastUsage <= MINUTE)
      addIfTrue(data, WAS_USED_IN_LAST_HOUR + suffix, timeSinceLastUsage <= HOUR)
      addIfTrue(data, WAS_USED_IN_LAST_DAY + suffix, timeSinceLastUsage <= DAY)
      addIfTrue(data, WAS_USED_IN_LAST_MONTH + suffix, timeSinceLastUsage <= (4 * WEEK.toLong()))
    }
  }

  internal fun addIfTrue(result: MutableMap<String, Any>, key: String, value: Boolean) {
    if (value) {
      result[key] = true
    }
  }
}