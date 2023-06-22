package com.intellij.searchEverywhereMl.ranking.features.statistician

import com.intellij.openapi.util.Key
import com.intellij.psi.statistics.Statistician
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager

internal class SearchEverywhereContributorStatistician : Statistician<String, String>() {
  companion object {
    private const val CONTEXT = "searchEverywhere#contributors#all"

    private const val KEY_NAME = "searchEverywhereContributor"
    private val KEY = Key.create<SearchEverywhereStatistician<in Any>>(KEY_NAME)

    fun increaseUseCount(contributorId: String) {
      StatisticsManager.getInstance().incUseCount(KEY, contributorId, CONTEXT)
    }

    fun getStatistics(): Statistics {
      val statsManager = StatisticsManager.getInstance()
      val stats = statsManager.getAllValues(CONTEXT)
        .asSequence()
        .associateWith { statsManager.getUseCount(it) }
        .mapKeys { it.key.value }
        .entries
        .map { it.toPair() }
        .sortedByDescending { it.second }

      return Statistics(stats)
    }
  }

  override fun serialize(contributorId: String, location: String) = StatisticsInfo(location, contributorId)

  /**
   * Usage of contributors, sorted by the descending usage count
   */
  data class Statistics(val contributorUsage: List<Pair<String, Int>>)
}
