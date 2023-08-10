package com.intellij.searchEverywhereMl.ranking.model

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

class SearchEverywhereModelProvider {
  private val cache: Cache<String, SearchEverywhereRankingModel> = Caffeine.newBuilder().maximumSize(modelCount.toLong()).build()
  private val modelCount: Int
    get() = SearchEverywhereMLRankingModelLoader.allLoaders.size

  internal fun getModel(tabId: String): SearchEverywhereRankingModel {
    return cache.get(tabId) {
      val loader = SearchEverywhereMLRankingModelLoader.getForTab(tabId)
      return@get SearchEverywhereRankingModel(loader.loadModel())
    }
  }
}