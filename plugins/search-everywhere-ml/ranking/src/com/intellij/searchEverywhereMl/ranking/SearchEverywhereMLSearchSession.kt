// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.ranking.features.FeaturesProviderCacheDataProvider
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereContextFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.features.statistician.SearchEverywhereStatisticianService
import com.intellij.searchEverywhereMl.ranking.features.statistician.increaseContributorUseCount
import com.intellij.searchEverywhereMl.ranking.id.SearchEverywhereMlOrderedItemIdProvider
import com.intellij.searchEverywhereMl.ranking.model.SearchEverywhereModelProvider
import com.intellij.searchEverywhereMl.ranking.performance.PerformanceTracker
import com.intellij.util.concurrency.NonUrgentExecutor
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMLSearchSession(project: Project?,
                                               val mixedListInfo: SearchEverywhereMixedListInfo,
                                               private val sessionId: Int,
                                               private val loggingRandomisation: FeaturesLoggingRandomisation) {
  val itemIdProvider = SearchEverywhereMlOrderedItemIdProvider()
  private val sessionStartTime: Long = System.currentTimeMillis()
  private val providersCache = FeaturesProviderCacheDataProvider().getDataToCache(project)
  private val modelProviderWithCache: SearchEverywhereModelProvider = SearchEverywhereModelProvider()

  // context features are calculated once per Search Everywhere session
  val cachedContextInfo: SearchEverywhereMLContextInfo = SearchEverywhereMLContextInfo(project)

  // search state is updated on each typing, tab or setting change
  // element features & ML score are also re-calculated on each typing because some of them might change, e.g. matching degree
  private val currentSearchState: AtomicReference<SearchEverywhereMlSearchState?> = AtomicReference<SearchEverywhereMlSearchState?>()
  private val logger: SearchEverywhereMLStatisticsCollector = SearchEverywhereMLStatisticsCollector()

  private val performanceTracker = PerformanceTracker()

  fun onSearchRestart(project: Project?,
                      experimentStrategy: SearchEverywhereMlExperiment,
                      reason: SearchRestartReason,
                      tabId: String,
                      orderByMl: Boolean,
                      keysTyped: Int,
                      backspacesTyped: Int,
                      searchQuery: String,
                      previousElementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>,
                      searchScope: ScopeDescriptor?,
                      isSearchEverywhere: Boolean) {
    val prevTimeToResult = performanceTracker.timeElapsed

    val prevState = currentSearchState.getAndUpdate { prevState ->
      val startTime = System.currentTimeMillis()
      val searchReason = if (prevState == null) SearchRestartReason.SEARCH_STARTED else reason
      val nextSearchIndex = (prevState?.searchIndex ?: 0) + 1
      val experimentGroup = experimentStrategy.experimentGroup
      performanceTracker.start()

      SearchEverywhereMlSearchState(
        sessionStartTime, startTime, nextSearchIndex, searchReason,
        tabId, experimentGroup, orderByMl,
        keysTyped, backspacesTyped, searchQuery, modelProviderWithCache, providersCache,
        project, searchScope, isSearchEverywhere
      )
    }

    if (prevState != null && experimentStrategy.isLoggingEnabledForTab(prevState.tabId)) {
      val shouldLogFeatures = loggingRandomisation.shouldLogFeatures(prevState.tabId)
      logger.onSearchRestarted(
        project, sessionId, shouldLogFeatures,
        itemIdProvider, cachedContextInfo, prevState,
        prevTimeToResult, mixedListInfo, previousElementsProvider
      )
    }
  }

  fun onItemSelected(project: Project?, experimentStrategy: SearchEverywhereMlExperiment,
                     indexes: IntArray, selectedItems: List<Any>, closePopup: Boolean,
                     elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    val state = getCurrentSearchState()
    if (state != null && experimentStrategy.isLoggingEnabledForTab(state.tabId)) {
      if (project != null) {
        val statisticianService = service<SearchEverywhereStatisticianService>()
        selectedItems.forEach { statisticianService.increaseUseCount(it) }

        if (state.tabId == SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID) {
          elementsProvider.invoke()
            .slice(indexes.asIterable())
            .forEach { increaseContributorUseCount(it.contributor.searchProviderId) }
        }
      }

      val shouldLogFeatures = loggingRandomisation.shouldLogFeatures(state.tabId)
      logger.onItemSelected(
        project, sessionId, shouldLogFeatures, itemIdProvider,
        state, indexes, selectedItems, closePopup,
        performanceTracker.timeElapsed, mixedListInfo,
        elementsProvider
      )
    }
  }

  fun onSearchFinished(project: Project?,
                       experimentStrategy: SearchEverywhereMlExperiment,
                       elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
    val state = getCurrentSearchState()
    if (state != null && experimentStrategy.isLoggingEnabledForTab(state.tabId)) {
      val shouldLogFeatures = loggingRandomisation.shouldLogFeatures(state.tabId)
      logger.onSearchFinished(
        project, sessionId, shouldLogFeatures, itemIdProvider,
        state, performanceTracker.timeElapsed, mixedListInfo,
        elementsProvider
      )
    }
  }

  fun notifySearchResultsUpdated() {
    performanceTracker.stop()
  }

  fun getCurrentSearchState() = currentSearchState.get()
}

internal class SearchEverywhereMLContextInfo(project: Project?) {
  val features: List<EventPair<*>> by lazy {
    SearchEverywhereContextFeaturesProvider().getContextFeatures(project)
  }

  init {
    NonUrgentExecutor.getInstance().execute {
      features  // We don't care about the value, we just want the features to be computed
    }
  }
}