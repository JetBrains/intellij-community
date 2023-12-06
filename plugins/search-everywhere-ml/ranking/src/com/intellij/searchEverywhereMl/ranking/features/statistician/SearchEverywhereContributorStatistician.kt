package com.intellij.searchEverywhereMl.ranking.features.statistician

import com.intellij.openapi.util.Key
import com.intellij.psi.statistics.Statistician
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager

private const val CONTEXT = "searchEverywhere#contributors#all"

private const val KEY_NAME = "searchEverywhereContributor"
private val KEY = Key.create<SearchEverywhereStatistician<in Any>>(KEY_NAME)

internal fun increaseContributorUseCount(contributorId: String) {
  StatisticsManager.getInstance().incUseCount(KEY, contributorId, CONTEXT)
}

internal fun getContributorStatistics(): ContributorStatistics {
  val statsManager = StatisticsManager.getInstance()
  val stats = statsManager.getAllValues(CONTEXT)
    .asSequence()
    .associateWith { statsManager.getUseCount(it) }
    .mapKeys { it.key.value }
    .entries
    .map { it.toPair() }
    .sortedByDescending { it.second }

  return ContributorStatistics(stats)
}

/**
 * Usage of contributors, sorted by the descending usage count
 */
internal data class ContributorStatistics(val contributorUsage: List<Pair<String, Int>>)

internal class SearchEverywhereContributorStatistician : Statistician<String, String>() {
  override fun serialize(contributorId: String, location: String) = StatisticsInfo(location, contributorId)
}
