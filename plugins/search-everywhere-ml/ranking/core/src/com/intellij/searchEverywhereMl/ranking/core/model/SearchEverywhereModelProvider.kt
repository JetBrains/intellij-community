package com.intellij.searchEverywhereMl.ranking.core.model

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab

class SearchEverywhereModelProvider {
  private val cache: Cache<SearchEverywhereTab.TabWithMlRanking, SearchEverywhereRankingModel> = Caffeine.newBuilder().maximumSize(modelCount.toLong()).build()
  private val modelCount: Int
    get() = SearchEverywhereMLRankingModelLoader.allLoaders.size

  internal fun getModel(tab: SearchEverywhereTab.TabWithMlRanking): SearchEverywhereRankingModel {
    return cache.get(tab) {
      if (isTabWithExactMatchIssue(tab))
        return@get getRankingModelForExactMatchIssue(tab)
      else
        return@get getRankingModel(tab)
    }
  }

  /**
   * We have tabs that suffer from the exact match issue, where the exactly matched result does not appear at the top,
   * we have introduced additional logic to address that issue.
   */
  private fun isTabWithExactMatchIssue(tab: SearchEverywhereTab): Boolean {
    return (tab == SearchEverywhereTab.Classes || tab == SearchEverywhereTab.Files) || isExactMatchExperiment(tab)
  }

  /**
   * True, if the provided tab is associated with the exact match manual fix experiment.
   */
  private fun isExactMatchExperiment(tab: SearchEverywhereTab): Boolean {
    if (tab !is SearchEverywhereTab.TabWithExperiments) return false
    return tab.currentExperimentType == SearchEverywhereMlExperiment.ExperimentType.ExactMatchManualFix
  }

  private fun getRankingModelForExactMatchIssue(tab: SearchEverywhereTab.TabWithMlRanking): SearchEverywhereRankingModel {
    val loader = SearchEverywhereMLRankingModelLoader.getForTab(tab)
    return ExactMatchSearchEverywhereRankingModel(loader.loadModel())
  }

  private fun getRankingModel(tab: SearchEverywhereTab.TabWithMlRanking): SearchEverywhereRankingModel {
    val loader = SearchEverywhereMLRankingModelLoader.getForTab(tab)
    return SimpleSearchEverywhereRankingModel(loader.loadModel())
  }
}