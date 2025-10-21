package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.EventCountRange
import com.intellij.internal.statistic.local.EventGlobalStatistics
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.roundDouble

internal abstract class GlobalStatisticsFields(
  val globalEventCountFieldName: String,
  val globalEventCountToMaxFieldName: String,
  val usersRatioFieldName: String,
  val eventPerUserRatioFieldName: String) {

  private val globalEventCount = EventFields.Long(globalEventCountFieldName)
  private val globalEventCountToMax = EventFields.Double(globalEventCountToMaxFieldName)
  private val usersRatio = EventFields.Double(usersRatioFieldName)
  private val eventCountPerUserRatio = EventFields.Double(eventPerUserRatioFieldName)

  fun getFieldsDeclaration(): List<EventField<*>> = listOf(globalEventCount, globalEventCountToMax, usersRatio, eventCountPerUserRatio)

  fun getEventGlobalStatistics(eventGlobalStatistics: EventGlobalStatistics?, maxEventCount: Long): List<EventPair<*>> {
    if (eventGlobalStatistics == null) return emptyList()

    return buildList {
        add(globalEventCount.with(eventGlobalStatistics.eventCount))
        if (maxEventCount != 0L) {
          add(globalEventCountToMax.with(roundDouble(eventGlobalStatistics.eventCount.toDouble() / maxEventCount)))
        }
        add(usersRatio.with(roundDouble(eventGlobalStatistics.usersRatio)))
        add(eventCountPerUserRatio.with(roundDouble(eventGlobalStatistics.eventCountPerUserRatio)))
    }
  }
}

internal class ContributorsGlobalStatisticsFields : GlobalStatisticsFields(
  globalEventCountFieldName = "globalSelections",
  globalEventCountToMaxFieldName = "globalSelectionsToMax",
  usersRatioFieldName = "usersRatio",
  eventPerUserRatioFieldName = "selectionsPerUserRatio"
)

internal class ActionsGlobalStatisticsFields(version: Int? = null) : GlobalStatisticsFields(
  globalEventCountFieldName = "globalUsage${versionSuffix(version)}",
  globalEventCountToMaxFieldName = "globalUsageToMax${versionSuffix(version)}",
  usersRatioFieldName = "usersRatio${versionSuffix(version)}",
  eventPerUserRatioFieldName = "usagesPerUserRatio${versionSuffix(version)}"
)

internal abstract class GlobalStatisticsContextFields(
  val globalMaxEventCountFieldName: String,
  val globalMinEventCountFieldName: String) {

  private val globalMaxEventCount = EventFields.Long(globalMaxEventCountFieldName)
  private val globalMinEventCount = EventFields.Long(globalMinEventCountFieldName)

  fun getFieldsDeclaration(): List<EventField<*>> = listOf(globalMaxEventCount, globalMinEventCount)

  fun getGlobalContextStatistics(eventCountRange: EventCountRange): List<EventPair<*>> {
    return listOf(
      globalMaxEventCount.with(eventCountRange.maxEventCount),
      globalMinEventCount.with(eventCountRange.minEventCount)
    )
  }
}

internal class ActionsGlobalStatisticsContextFields(version: Int? = null) : GlobalStatisticsContextFields(
  globalMaxEventCountFieldName = "globalMaxUsage${versionSuffix(version)}",
  globalMinEventCountFieldName = "globalMinUsage${versionSuffix(version)}"
)

internal class ContributorsGlobalStatisticsContextFields : GlobalStatisticsContextFields(
  globalMaxEventCountFieldName = "globalMaxSelection",
  globalMinEventCountFieldName = "globalMinSelection"
)

private fun versionSuffix(version: Int?): String {
  return version?.let { "V$it" } ?: ""
}