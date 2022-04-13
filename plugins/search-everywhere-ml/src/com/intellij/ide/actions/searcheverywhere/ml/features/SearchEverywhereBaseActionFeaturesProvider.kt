package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.TopHitSEContributor
import com.intellij.ide.util.gotoByName.GotoActionItemProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.util.Time.*

internal abstract class SearchEverywhereBaseActionFeaturesProvider
  : SearchEverywhereElementFeaturesProvider(ActionSearchEverywhereContributor::class.java, TopHitSEContributor::class.java) {
  companion object {
    internal const val IS_ENABLED = "isEnabled"

    private const val ITEM_TYPE = "type"
    private const val TYPE_WEIGHT = "typeWeight"
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
    val data: MutableMap<String, Any> = hashMapOf()
    addIfTrue(data, IS_HIGH_PRIORITY, isHighPriority(elementPriority))

    // (element is GotoActionModel.MatchedValue) for actions and option provided by 'ActionSearchEverywhereContributor'
    // (element is OptionDescription || element is AnAction) for actions and option provided by 'TopHitSEContributor'
    if (element is GotoActionModel.MatchedValue) {
      data[ITEM_TYPE] = element.type
      data[TYPE_WEIGHT] = element.valueTypeWeight
    }

    val value = if (element is GotoActionModel.MatchedValue) element.value else element
    val actionText = GotoActionItemProvider.getActionText(value)
    actionText?.let {
      data.putAll(getNameMatchingFeatures(it, searchQuery))
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
  abstract fun getFeatures(data: MutableMap<String, Any>, currentTime: Long, value: Any): Map<String, Any>

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