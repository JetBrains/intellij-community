package com.intellij.searchEverywhereMl.ranking.core.model

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking

class SearchEverywhereModelProvider {
  private val cache: Cache<String, SearchEverywhereRankingModel> = Caffeine.newBuilder().maximumSize(modelCount.toLong()).build()
  private val modelCount: Int
    get() = SearchEverywhereMLRankingModelLoader.allLoaders.size

  internal fun getModel(tabId: String): SearchEverywhereRankingModel {
    return cache.get(tabId) {
      val isExactMatchExperiment = SearchEverywhereTabWithMlRanking.findById(tabId)?.let { tab ->
        SearchEverywhereMlExperiment().getExperimentForTab(tab) == SearchEverywhereMlExperiment.ExperimentType.EXACT_MATCH_PRIORITIZATION
      } ?: false
      val loader = SearchEverywhereMLRankingModelLoader.getForTab(tabId)
      val model = if (isExactMatchExperiment) ExactMatchSearchEverywhereRankingModel(loader.loadModel()) else SimpleSearchEverywhereRankingModel(loader.loadModel())
      return@get model
    }
  }
}