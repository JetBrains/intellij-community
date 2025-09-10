package com.intellij.searchEverywhereMl.ranking.core.features.statistician

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.ContributorsLocalSummary
import com.intellij.internal.statistic.local.ContributorsSelectionsRange
import com.intellij.openapi.components.service
import com.intellij.searchEverywhereMl.ranking.core.features.addIfTrue

internal class ContributorsLocalStatisticsFields {

  companion object {
    val SELECTION_ALL = EventFields.Int("selectionAll")
    val SELECTION_OTHER = EventFields.Int("selectionOther")
    val SELECTION_TO_MAX_ALL = EventFields.Double("selectionToMaxAll")
    val SELECTION_TO_MAX_OTHER = EventFields.Double("selectionToMaxOther")
    val TIME_SINCE_LAST_SELECTION_ALL = EventFields.Long("timeSinceLastSelectionAll")
    val TIME_SINCE_LAST_SELECTION_OTHER = EventFields.Long("timeSinceLastSelectionOther")
    val WAS_SELECTED_IN_LAST_MINUTE_ALL = EventFields.Boolean("wasSelectedInLastMinuteAll")
    val WAS_SELECTED_IN_LAST_MINUTE_OTHER = EventFields.Boolean("wasSelectedInLastMinuteOther")
    val WAS_SELECTED_IN_LAST_HOUR_ALL = EventFields.Boolean("wasSelectedInLastHourAll")
    val WAS_SELECTED_IN_LAST_HOUR_OTHER = EventFields.Boolean("wasSelectedInLastHourOther")
    val WAS_SELECTED_IN_LAST_DAY_ALL = EventFields.Boolean("wasSelectedInLastDayAll")
    val WAS_SELECTED_IN_LAST_DAY_OTHER = EventFields.Boolean("wasSelectedInLastDayOther")
    val WAS_SELECTED_IN_LAST_MONTH_ALL = EventFields.Boolean("wasSelectedInLastMonthAll")
    val WAS_SELECTED_IN_LAST_MONTH_OTHER = EventFields.Boolean("wasSelectedInLastMonthOther")
  }

  fun getFieldsDeclaration(): List<EventField<*>> = listOf(
    SELECTION_ALL,
    SELECTION_OTHER,
    SELECTION_TO_MAX_ALL,
    SELECTION_TO_MAX_OTHER,
    TIME_SINCE_LAST_SELECTION_ALL,
    TIME_SINCE_LAST_SELECTION_OTHER,
    WAS_SELECTED_IN_LAST_MINUTE_ALL,
    WAS_SELECTED_IN_LAST_MINUTE_OTHER,
    WAS_SELECTED_IN_LAST_HOUR_ALL,
    WAS_SELECTED_IN_LAST_HOUR_OTHER,
    WAS_SELECTED_IN_LAST_DAY_ALL,
    WAS_SELECTED_IN_LAST_DAY_OTHER,
    WAS_SELECTED_IN_LAST_MONTH_ALL,
    WAS_SELECTED_IN_LAST_MONTH_OTHER)

  fun getLocalStatistics(contributorId: String, sessionStartTime: Long): List<EventPair<*>> {
    val localSummary = service<ContributorsLocalSummary>()
    val summary = localSummary.getContributorStatsById(contributorId) ?: return emptyList()
    val contributorsSelectionRange = localSummary.getContributorsSelectionsRange()

    val localStatsAll = getSelectionAndTimeStatistics(summary.allTabSelectionCount, contributorsSelectionRange.maxAllTabSelectionCount,
                                                      sessionStartTime, summary.allTabLastSelectedTimestamp, true)
    val localStatsOther = getSelectionAndTimeStatistics(summary.otherTabsSelectionCount,
                                                        contributorsSelectionRange.maxOtherTabsSelectionCount,
                                                        sessionStartTime, summary.otherTabsLastSelectedTimestamp, false)

    return localStatsAll + localStatsOther
  }


  private fun getSelectionAndTimeStatistics(selection: Int, maxSelection: Int, sessionStartTime: Long, lastSelectedTime: Long,
                                 isFromAll: Boolean): List<EventPair<*>> {
    val selectionStats = getSelectionStatistics(selection, maxSelection, isFromAll)
    val lastTimeSelectedStats = getLastTimeSelectedStatistics(sessionStartTime, lastSelectedTime, isFromAll)
    return selectionStats + lastTimeSelectedStats
  }

  private fun getSelectionStatistics(selection: Int, maxSelection: Int, isFromAll: Boolean) : List<EventPair<*>> {
    if (selection <= 0) return emptyList()

    return buildList {
      val selectionType = if (isFromAll) SELECTION_ALL else SELECTION_OTHER
      add(selectionType.with(selection))

      if (maxSelection != 0) {
        val selectionToMaxType = if (isFromAll) SELECTION_TO_MAX_ALL else SELECTION_TO_MAX_OTHER
        add(selectionToMaxType.with(selection.toDouble() / maxSelection))
      }
    }
  }

  private fun getLastTimeSelectedStatistics(sessionStartTime: Long, lastSelectedTime: Long,
                                            isFromAll: Boolean) : List<EventPair<*>> {
    if (lastSelectedTime <= 0) return emptyList()

    return buildList {
      val timeSinceLastSelection = sessionStartTime - lastSelectedTime
      add((if (isFromAll) TIME_SINCE_LAST_SELECTION_ALL else TIME_SINCE_LAST_SELECTION_OTHER).with(timeSinceLastSelection))

      addIfTrue(if (isFromAll) WAS_SELECTED_IN_LAST_MINUTE_ALL else WAS_SELECTED_IN_LAST_MINUTE_OTHER,
                timeSinceLastSelection <= 60 * 1000)
      addIfTrue(if (isFromAll) WAS_SELECTED_IN_LAST_HOUR_ALL else WAS_SELECTED_IN_LAST_HOUR_OTHER,
                timeSinceLastSelection <= 60 * 60 * 1000)
      addIfTrue(if (isFromAll) WAS_SELECTED_IN_LAST_DAY_ALL else WAS_SELECTED_IN_LAST_DAY_OTHER,
                timeSinceLastSelection <= 24 * 60 * 60 * 1000)
      addIfTrue(if (isFromAll) WAS_SELECTED_IN_LAST_MONTH_ALL else WAS_SELECTED_IN_LAST_MONTH_OTHER,
                timeSinceLastSelection <= 30 * 24 * 60 * 60 * 1000L)
    }
  }
}

internal class ContributorsLocalStatisticsContextFields {

  companion object {
    val MAX_SELECTION_ALL = EventFields.Int("maxSelectionAll")
    val MIN_SELECTION_ALL = EventFields.Int("minSelectionAll")
    val MAX_SELECTION_OTHER = EventFields.Int("maxSelectionOther")
    val MIN_SELECTION_OTHER = EventFields.Int("minSelectionOther")
  }

  fun getFieldsDeclaration(): List<EventField<*>> = listOf(MAX_SELECTION_ALL, MIN_SELECTION_ALL, MAX_SELECTION_OTHER,
                                                           MIN_SELECTION_OTHER)

  fun getLocalContextStatistics(contributorsSelectionsRange: ContributorsSelectionsRange): List<EventPair<*>> {
    return listOf(
      MAX_SELECTION_ALL.with(contributorsSelectionsRange.maxAllTabSelectionCount),
      MIN_SELECTION_ALL.with(contributorsSelectionsRange.minAllTabSelectionCount),
      MAX_SELECTION_OTHER.with(contributorsSelectionsRange.maxOtherTabsSelectionCount),
      MIN_SELECTION_OTHER.with(contributorsSelectionsRange.minOtherTabsSelectionCount)
    )
  }
}