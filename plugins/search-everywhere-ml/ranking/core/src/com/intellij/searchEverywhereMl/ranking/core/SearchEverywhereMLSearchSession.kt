// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.TextEmbeddingProvider
import com.intellij.searchEverywhereMl.isLoggingEnabled
import com.intellij.searchEverywhereMl.ranking.core.features.FeaturesProviderCacheDataProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContextFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.SearchEverywhereStatisticianService
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.increaseContributorUseCount
import com.intellij.searchEverywhereMl.ranking.core.id.MissingKeyProviderCollector
import com.intellij.searchEverywhereMl.ranking.core.id.SearchEverywhereMlOrderedItemIdProvider
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereModelProvider
import com.intellij.searchEverywhereMl.ranking.core.performance.PerformanceTracker
import com.intellij.searchEverywhereMl.ranking.core.utils.convertNameToNaturalLanguage
import com.intellij.util.concurrency.NonUrgentExecutor
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMLSearchSession(
  project: Project?,
  val mixedListInfo: SearchEverywhereMixedListInfo,
  private val sessionId: Int,
) {
  val itemIdProvider = SearchEverywhereMlOrderedItemIdProvider { MissingKeyProviderCollector.addMissingProviderForClass(it::class.java) }

  val sessionStartTime: Long = System.currentTimeMillis()
  private val providersCache = FeaturesProviderCacheDataProvider().getDataToCache(project)
  private val modelProviderWithCache: SearchEverywhereModelProvider = SearchEverywhereModelProvider()
  private val featureCache = SearchEverywhereMlFeaturesCache()
  private val embeddingCache = ConcurrentCollectionFactory.createConcurrentMap<String, FloatTextEmbedding>()

  // context features are calculated once per Search Everywhere session
  val cachedContextInfo: SearchEverywhereMLContextInfo = SearchEverywhereMLContextInfo(project)

  // search state is updated on each typing, tab or setting change
  // element features & ML score are also re-calculated on each typing because some of them might change, e.g. matching degree
  private val currentSearchState: AtomicReference<SearchEverywhereMlSearchState?> = AtomicReference<SearchEverywhereMlSearchState?>()
  private val logger: SearchEverywhereMLStatisticsCollector = SearchEverywhereMLStatisticsCollector

  private val performanceTracker = PerformanceTracker()

  fun onSearchRestart(
    project: Project?,
    reason: SearchRestartReason,
    tabId: String,
    orderByMl: Boolean,
    keysTyped: Int,
    backspacesTyped: Int,
    searchQuery: String,
    previousElementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>,
    searchScope: ScopeDescriptor?,
    isSearchEverywhere: Boolean,
  ) {
    val tab = SearchEverywhereTab.findById(tabId) ?: return
    val prevTimeToResult = performanceTracker.timeElapsed

    val prevState = currentSearchState.getAndUpdate { prevState ->
      val startTime = System.currentTimeMillis()
      val searchReason = if (prevState == null) SearchRestartReason.SEARCH_STARTED else reason
      val nextSearchIndex = (prevState?.searchIndex ?: 0) + 1
      val experimentGroup = SearchEverywhereMlExperiment.experimentGroup
      performanceTracker.start()

      SearchEverywhereMlSearchState(
        sessionStartTime, startTime, nextSearchIndex, searchReason,
        tab, experimentGroup, orderByMl,
        keysTyped, backspacesTyped, searchQuery, modelProviderWithCache, providersCache, mixedListInfo,
        project, searchScope, isSearchEverywhere
      )
    }

    if (prevState != null && prevState.tab.isLoggingEnabled()) {
      logger.onSearchRestarted(
        project, sessionId,
        itemIdProvider, cachedContextInfo, prevState, featureCache,
        prevTimeToResult, mixedListInfo, previousElementsProvider
      )
    }
  }

  fun onItemSelected(
    project: Project?,
    indexes: IntArray, selectedItems: List<Any>, closePopup: Boolean,
    elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>,
  ) {
    val state = getCurrentSearchState()
    if (state != null && state.tab.isLoggingEnabled()) {
      if (project != null) {
        val statisticianService = service<SearchEverywhereStatisticianService>()
        selectedItems.forEach { statisticianService.increaseUseCount(it) }

        if (state.tab == SearchEverywhereTab.All) {
          elementsProvider.invoke()
            .slice(indexes.asIterable())
            .forEach { increaseContributorUseCount(it.contributor.searchProviderId) }
        }
      }

      val sessionEndTime = System.currentTimeMillis()
      val sessionDuration = (sessionEndTime - sessionStartTime).toInt()

      logger.onItemSelected(
        project, sessionId, itemIdProvider,
        state, featureCache, indexes, selectedItems, closePopup,
        performanceTracker.timeElapsed, mixedListInfo,
        elementsProvider, sessionDuration
      )
    }

    if (closePopup) {
      MissingKeyProviderCollector.report(sessionId)
    }
  }

  fun onSearchFinished(
    project: Project?,
    elementsProvider: () -> List<SearchEverywhereFoundElementInfoWithMl>,
  ) {
    val state = getCurrentSearchState()

    val sessionEndTime = System.currentTimeMillis()
    val sessionDuration = (sessionEndTime - sessionStartTime).toInt()

    if (state != null && state.tab.isLoggingEnabled()) {
      logger.onSearchFinished(
        project, sessionId, itemIdProvider,
        state, featureCache, performanceTracker.timeElapsed, mixedListInfo,
        elementsProvider, sessionDuration
      )
    }

    MissingKeyProviderCollector.report(sessionId)
  }

  fun notifySearchResultsUpdated() {
    performanceTracker.stop()
  }

  fun getCurrentSearchState() = currentSearchState.get()

  fun getSearchQueryEmbedding(searchQuery: String, split: Boolean): FloatTextEmbedding? {
    return embeddingCache[searchQuery]
           ?: TextEmbeddingProvider.getProvider()?.embed(if (split) convertNameToNaturalLanguage(searchQuery) else searchQuery)
             ?.also { embeddingCache[searchQuery] = it }
  }
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