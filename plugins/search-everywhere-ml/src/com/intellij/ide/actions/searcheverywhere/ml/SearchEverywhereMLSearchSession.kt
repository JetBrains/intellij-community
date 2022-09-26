// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.ml.features.FeaturesProviderCacheDataProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContextFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.statistician.SearchEverywhereContributorStatistician
import com.intellij.ide.actions.searcheverywhere.ml.features.statistician.SearchEverywhereStatisticianService
import com.intellij.ide.actions.searcheverywhere.ml.id.SearchEverywhereMlItemIdProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereModelProvider
import com.intellij.ide.actions.searcheverywhere.ml.performance.PerformanceTracker
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.NonUrgentExecutor
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMLSearchSession(project: Project?,
                                               val mixedListInfo: SearchEverywhereMixedListInfo,
                                               private val sessionId: Int,
                                               private val loggingRandomisation: FeaturesLoggingRandomisation) {
  val itemIdProvider = SearchEverywhereMlItemIdProvider()
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
                      previousElementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>) {
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
        keysTyped, backspacesTyped, searchQuery, modelProviderWithCache, providersCache
      )
    }

    if (prevState != null && experimentStrategy.isLoggingEnabledForTab(prevState.tabId)) {
      val shouldLogFeatures = loggingRandomisation.shouldLogFeatures(prevState.tabId)
      logger.onSearchRestarted(
        project, sessionId, prevState.searchIndex,
        shouldLogFeatures, itemIdProvider, cachedContextInfo,
        prevState, prevTimeToResult, mixedListInfo, previousElementsProvider
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
            .forEach { SearchEverywhereContributorStatistician.increaseUseCount(it.contributor.searchProviderId) }
        }
      }

      val shouldLogFeatures = loggingRandomisation.shouldLogFeatures(state.tabId)
      logger.onItemSelected(
        project, sessionId, state.searchIndex,
        shouldLogFeatures, state.experimentGroup,
        state.orderByMl, itemIdProvider, cachedContextInfo,
        state, indexes, selectedItems,
        closePopup, performanceTracker.timeElapsed, mixedListInfo, elementsProvider
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
        project, sessionId, state.searchIndex,
        shouldLogFeatures, state.experimentGroup,
        state.orderByMl, itemIdProvider, cachedContextInfo,
        state, performanceTracker.timeElapsed, mixedListInfo, elementsProvider
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