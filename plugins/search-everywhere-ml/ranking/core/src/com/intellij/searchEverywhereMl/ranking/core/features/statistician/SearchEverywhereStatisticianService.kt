package com.intellij.searchEverywhereMl.ranking.core.features.statistician

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Service(Service.Level.APP)
class SearchEverywhereStatisticianService(private val coroutineScope: CoroutineScope) {
  companion object {
    val KEY: Key<SearchEverywhereStatistician<in Any>> = Key.create("searchEverywhere")
  }

  fun increaseUseCount(element: Any) {
    coroutineScope.launch {
      val info = readAction { getSerializedInfo(element) } ?: return@launch
      val manager = StatisticsManager.getInstance()

      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          manager.incUseCount(info)
        }
      }
    }
  }

  fun getCombinedStats(element: Any): SearchEverywhereStatisticianStats? {
    val statisticsManager = StatisticsManager.getInstance()

    val info = ApplicationManager.getApplication().runReadAction(
      Computable<StatisticsInfo?> { getSerializedInfo(element) }
    ) ?: return null

    val allValues = statisticsManager.getAllValues(info.context).associateWith { statisticsManager.getUseCount(it) }
    val useCount = allValues.entries.firstOrNull { it.key.value == info.value }?.value ?: 0
    val isMostPopular = useCount > 0 && useCount == allValues.maxOf { it.value }
    val recency = statisticsManager.getLastUseRecency(info)

    return SearchEverywhereStatisticianStats(useCount, isMostPopular, recency)
  }

  private fun getSerializedInfo(element: Any) = StatisticsManager.serialize(KEY, element, "")  // location is obtained from element
}
