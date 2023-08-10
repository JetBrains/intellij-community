package com.intellij.searchEverywhereMl.ranking.features.statistician

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Key
import com.intellij.psi.statistics.StatisticsManager


@Service(Service.Level.APP)
class SearchEverywhereStatisticianService {
  companion object {
    val KEY: Key<SearchEverywhereStatistician<in Any>> = Key.create("searchEverywhere")
  }

  fun increaseUseCount(element: Any) = getSerializedInfo(element)?.let { StatisticsManager.getInstance().incUseCount(it) }

  fun getCombinedStats(element: Any): SearchEverywhereStatisticianStats? {
    val statisticsManager = StatisticsManager.getInstance()
    val info = getSerializedInfo(element) ?: return null

    val allValues = statisticsManager.getAllValues(info.context).associateWith { statisticsManager.getUseCount(it) }
    val useCount = allValues.entries.firstOrNull { it.key.value == info.value }?.value ?: 0
    val isMostPopular = useCount > 0 && useCount == allValues.maxOf { it.value }
    val recency = statisticsManager.getLastUseRecency(info)

    return SearchEverywhereStatisticianStats(useCount, isMostPopular, recency)
  }

  private fun getSerializedInfo(element: Any) = StatisticsManager.serialize(KEY, element, "")  // location is obtained from element
}
