package com.intellij.ide.actions.searcheverywhere.ml.features.statistician

internal data class SearchEverywhereStatisticianStats(val useCount: Int,
                                                      val isMostPopular: Boolean,
                                                      val recency: Int) {
  val isMostRecent = recency == 0
}
