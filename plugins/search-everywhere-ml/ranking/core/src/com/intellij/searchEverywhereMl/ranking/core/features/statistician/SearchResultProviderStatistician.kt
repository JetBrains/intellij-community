package com.intellij.searchEverywhereMl.ranking.core.features.statistician

import com.intellij.openapi.util.Key
import com.intellij.psi.statistics.Statistician
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager

private const val CONTEXT = "searchEverywhere#contributors#all"

private const val KEY_NAME = "searchEverywhereContributor"
private val KEY = Key.create<SearchEverywhereStatistician<in Any>>(KEY_NAME)

internal fun increaseProvidersUseCount(providerId: String) {
  StatisticsManager.getInstance().incUseCount(KEY, providerId, CONTEXT)
}

internal fun getProviderStatistics(): SeItemsProviderStatistics {
  val statsManager = StatisticsManager.getInstance()
  val stats = statsManager.getAllValues(CONTEXT)
    .asSequence()
    .associateWith { statsManager.getUseCount(it) }
    .mapKeys { it.key.value }
    .entries
    .map { it.toPair() }
    .sortedByDescending { it.second }

  return SeItemsProviderStatistics(stats)
}

/**
 * Usage of contributors, sorted by the descending usage count
 */
internal data class SeItemsProviderStatistics(val contributorUsage: List<Pair<String, Int>>)

internal class SearchResultProviderStatistician : Statistician<String, String>() {
  override fun serialize(providerId: String, location: String) = StatisticsInfo(location, providerId)
}
