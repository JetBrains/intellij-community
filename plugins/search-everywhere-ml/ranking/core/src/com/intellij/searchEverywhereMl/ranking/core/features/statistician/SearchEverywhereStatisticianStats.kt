package com.intellij.searchEverywhereMl.ranking.core.features.statistician

data class SearchEverywhereStatisticianStats(val useCount: Int,
                                             val isMostPopular: Boolean,
                                             val recency: Int) {
  val isMostRecent = recency == 0
}
