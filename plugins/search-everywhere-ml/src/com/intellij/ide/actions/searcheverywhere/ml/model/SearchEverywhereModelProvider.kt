package com.intellij.ide.actions.searcheverywhere.ml.model

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.internal.ml.DecisionFunction

class SearchEverywhereModelProvider {
  private val cache: Cache<String, DecisionFunction> = Caffeine.newBuilder().maximumSize(3).build()

  fun getModel(tabId: String): DecisionFunction {
    return cache.get(tabId) {
      val loader = SearchEverywhereMLRankingModelLoader.getForTab(tabId)
      return@get loader.loadModel()
    }
  }
}