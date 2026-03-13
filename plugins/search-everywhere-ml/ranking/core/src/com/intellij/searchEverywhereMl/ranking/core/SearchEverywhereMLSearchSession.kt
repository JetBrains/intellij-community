// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.TextEmbeddingProvider
import com.intellij.searchEverywhereMl.isLoggingEnabled
import com.intellij.searchEverywhereMl.isTabWithMlRanking
import com.intellij.searchEverywhereMl.ranking.core.adapters.MlProbability
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.LegacyContributorAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SessionWideId
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultProviderAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.searchEverywhereMl.ranking.core.adapters.StateLocalId
import com.intellij.searchEverywhereMl.ranking.core.features.*
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.ML_SCORE_KEY
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.SearchEverywhereStatisticianService
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.increaseProvidersUseCount
import com.intellij.searchEverywhereMl.ranking.core.id.MissingKeyProviderCollector
import com.intellij.searchEverywhereMl.ranking.core.id.SearchEverywhereMlOrderedItemIdProvider
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereModelProvider
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereRankingModel
import com.intellij.searchEverywhereMl.ranking.core.performance.PerformanceTracker
import com.intellij.searchEverywhereMl.ranking.core.utils.convertNameToNaturalLanguage
import com.intellij.util.applyIf
import com.intellij.util.concurrency.NonUrgentExecutor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMLSearchSession private constructor(
  val project: Project?,
  val sessionId: Int,
  private val providersInfo: SearchResultProvidersInfo,
) {
  companion object {
    private val LOG = logger<SearchEverywhereMLSearchSession>()
    private val sessionIdCounter: AtomicInteger = AtomicInteger(0)

    fun createNext(
      project: Project?,
      providersInfo: SearchResultProvidersInfo = SearchResultProvidersInfo.EMPTY,
    ): SearchEverywhereMLSearchSession {
      val session = SearchEverywhereMLSearchSession(project, sessionIdCounter.incrementAndGet(), providersInfo)
      LOG.trace("Session created: sessionId=${session.sessionId}, project=$project")
      return session
    }
  }

  val sessionWideIdProvider =
    SearchEverywhereMlOrderedItemIdProvider { MissingKeyProviderCollector.addMissingProviderForClass(it::class.java) }

  val sessionStartTime: Long = System.currentTimeMillis()
  private val providersCache = FeaturesProviderCacheDataProvider().getDataToCache(project)
  private val modelProviderWithCache: SearchEverywhereModelProvider = SearchEverywhereModelProvider()
  private val embeddingCache = ConcurrentCollectionFactory.createConcurrentMap<String, FloatTextEmbedding>()

  // context features are calculated once per Search Everywhere session
  val cachedContextInfo: SearchEverywhereMLContextInfo = SearchEverywhereMLContextInfo(project)

  // search state is updated on each typing, tab or setting change
  // element features & ML score are also re-calculated on each typing because some of them might change, e.g. matching degree
  private val _activeState: AtomicReference<SearchState?> = AtomicReference<SearchState?>()

  val activeState: SearchState?
    get() = _activeState.get()

  private val stateHistory: MutableList<SearchState> = mutableListOf()

  val previousSearchState: SearchState?
    get() = stateHistory.lastOrNull()

  private val performanceTracker = PerformanceTracker()

  fun onSessionStarted(tabId: String, isNewSearchEverywhere: Boolean) {
    val tab = SearchEverywhereTab.getById(tabId)
    LOG.trace("Session started: sessionId=$sessionId, tab=$tab, isNew=$isNewSearchEverywhere")
    SearchEverywhereMLStatisticsCollector.onSessionStarted(project, sessionId, tab, isNewSearchEverywhere,
                                                           sessionStartTime, cachedContextInfo.features, providersInfo.isMixedList)
  }

  fun onStateStarted(
    tabId: String, query: String, reason: SearchStateChangeReason, scopeDescriptor: ScopeDescriptor?,
    isSearchEverywhere: Boolean, searchFilter: SeFilterState? = null, isDumbMode: Boolean = false,
  ) {
    val tab = SearchEverywhereTab.getById(tabId)

    finishUnfinishedActiveState()

    val previousState = stateHistory.lastOrNull()
    val stateChangeReason = if (previousState == null) SearchStateChangeReason.SEARCH_START else reason
    val nextSearchIndex = (previousState?.index ?: 0) + 1
    val newSearchState = SearchState(nextSearchIndex, tab, scopeDescriptor, isSearchEverywhere, stateChangeReason, query,
                                     searchFilter, isDumbMode)

    LOG.trace("Session $sessionId: State started: stateIndex=$nextSearchIndex, tab=$tab, query='$query', reason=$stateChangeReason, scope=$scopeDescriptor, everywhere=$isSearchEverywhere")
    _activeState.set(newSearchState)
    performanceTracker.start()
  }

  fun onStateFinished(results: List<SearchResultAdapter.Raw>) {
    val finishedState = _activeState.getAndSet(null)
    if (finishedState == null) {
      LOG.trace("Session $sessionId: State finished called but no active state (already finished)")
      return // Already finished, ignore duplicate call
    }

    finishedState.markAsFinished()
    LOG.trace("Session $sessionId: State finished: stateIndex=${finishedState.index}, resultsCount=${results.size}")
    stateHistory.add(finishedState)

    val processedResults = getProcessedResults(results)

    SearchEverywhereMLStatisticsCollector.onSearchRestarted(project, this, finishedState, processedResults, performanceTracker.timeElapsed)
  }

  fun onItemsSelected(selectedItems: List<Pair<Int, SearchResultAdapter.Raw>>) {
    if (selectedItems.isEmpty()) return

    val candidateStates = stateHistory + listOfNotNull(activeState)
    val state = candidateStates.lastOrNull {
      it.getProcessedResultByIdOrNull(selectedItems.first().second.stateLocalId) != null
    }

    if (state == null) {
      LOG.debug("Selected items ${selectedItems.map { it.second.stateLocalId }} were not processed by any state within this search session")
      error("Selected items were not processed by any state within this search session")
    }

    if (!state.tab.isLoggingEnabled()) return

    val processedSelectedItems = selectedItems.map { (index, raw) ->
      index to state.getProcessedSearchResultById(raw.stateLocalId)
    }

    LOG.trace("Session $sessionId: Selected ${selectedItems.size} items: $processedSelectedItems")

    val statisticianService = service<SearchEverywhereStatisticianService>()
    processedSelectedItems.forEach { (index, result) ->
      statisticianService.increaseUseCount(result)

      if (state.tab == SearchEverywhereTab.All) {
        increaseProvidersUseCount(result.provider.id)
      }

      SearchEverywhereMLStatisticsCollector.onItemSelected(project, sessionId, state.index, index to result)
    }
  }

  fun onSessionFinished() {
    val sessionEndTime = System.currentTimeMillis()
    val sessionDuration = (sessionEndTime - sessionStartTime).toInt()

    finishUnfinishedActiveState()

    val lastState = checkNotNull(stateHistory.last()) { "No previous search state found " }

    LOG.trace("Session finished: sessionId=$sessionId, duration=${sessionDuration}ms, lastStateIndex=${lastState.index}")
    SearchEverywhereMLStatisticsCollector.onSessionFinished(project, sessionId, lastState.tab, sessionDuration)
    MissingKeyProviderCollector.report(sessionId)
  }

  private fun finishUnfinishedActiveState() {
    // If there's an active state that wasn't properly finished (e.g., due to rapid query changes),
    // finish it first with its cached results before starting a new one
    val unfinishedState = _activeState.getAndSet(null) ?: return

    unfinishedState.markAsInterrupted()
    val cachedResults = unfinishedState.getCachedResults()
    LOG.trace("Session $sessionId: Finishing interrupted state ${unfinishedState.index} before starting new state, cachedResultsCount=${cachedResults.size}")
    stateHistory.add(unfinishedState)
    SearchEverywhereMLStatisticsCollector.onSearchRestarted(project, this, unfinishedState, cachedResults, performanceTracker.timeElapsed)
  }


  fun notifySearchResultsUpdated() {
    if (activeState != null) {
      performanceTracker.stop()
    }
  }

  fun getSearchQueryEmbedding(searchQuery: String, split: Boolean): FloatTextEmbedding? {
    return embeddingCache[searchQuery]
           ?: TextEmbeddingProvider.getProvider()?.embed(if (split) convertNameToNaturalLanguage(searchQuery) else searchQuery)
             ?.also { embeddingCache[searchQuery] = it }
  }

  private fun getProcessedResults(rawResults: List<SearchResultAdapter.Raw>): List<SearchResultAdapter.Processed> {
    val candidateStates = stateHistory + listOfNotNull(activeState).asReversed()
    
    return rawResults
      .map { raw ->
        candidateStates.firstNotNullOfOrNull {
          it.getProcessedResultByIdOrNull(raw.stateLocalId)
        } ?: error("Result ${raw.stateLocalId} was not processed by any search state")
      }
  }

  inner class SearchState(
    val index: Int,
    val tab: SearchEverywhereTab,
    val searchScope: ScopeDescriptor?,
    val isSearchEverywhere: Boolean,
    val searchStateChangeReason: SearchStateChangeReason,
    val query: String,
    val searchFilter: SeFilterState? = null,
    val isDumbMode: Boolean = false,
  ) {
    var isFinished: Boolean = false
      private set

    fun markAsFinished() {
      isFinished = true
    }

    /**
     * Indicates whether this search state was interrupted before it could complete.
     * A state is considered interrupted when a new search state starts before the current one finishes.
     */
    var wasInterrupted: Boolean = false
      private set

    fun markAsInterrupted() {
      wasInterrupted = true
      markAsFinished()
    }

    /**
     * Returns all processed search results that have been cached in this state.
     * This is useful for retrieving partial results when a state is interrupted.
     */
    fun getCachedResults(): List<SearchResultAdapter.Processed> {
      return searchResultCache.values.toList()
    }

    val project: Project?
      get() = this@SearchEverywhereMLSearchSession.project

    val searchStateFeatures = SearchEverywhereStateFeaturesProvider.getFeatures(this)

    val orderByMl: Boolean
      get() {
        if (!tab.isTabWithMlRanking()) {
          return false
        }

        if (tab == SearchEverywhereTab.All && query.isEmpty()) {
          return false
        }

        if (tab == SearchEverywhereTab.Actions && query.isEmpty()) {
          // Don't sort recently used actions which appear on an empty query
          return false
        }

        return tab.isMlRankingEnabled
      }

    private val model: SearchEverywhereRankingModel by lazy { modelProviderWithCache.getModel(tab as SearchEverywhereTab.TabWithMlRanking) }

    private val searchResultCache: MutableMap<StateLocalId, SearchResultAdapter.Processed> = mutableMapOf()
    private val providerWeightsById: MutableMap<String, Int> = mutableMapOf()

    /**
     * Processes a raw search result to transform it into a processed search result,
     * calculating machine learning features and probability if enabled.
     *
     * The processed result is cached for further use.
     *
     * @param searchResult The raw search result to be processed. Must implement the `SearchResultAdapter.Raw` interface
     *                     and provide a fetchable raw item.
     * @param correction   The spelling correction result, which determines if updates such as typo corrections
     *                     should influence certain features. Defaults to `SearchEverywhereSpellCheckResult.NoCorrection`.
     * @return A `SearchResultAdapter.Processed` instance if the raw item exists otherwise, returns null.
     */
    fun processSearchResult(
      searchResult: SearchResultAdapter.Raw,
      correction: SearchEverywhereSpellCheckResult = SearchEverywhereSpellCheckResult.NoCorrection,
    ): SearchResultAdapter.Processed {
      searchResult.providerWeight?.let {
        providerWeightsById.putIfAbsent(searchResult.provider.id, it)
      }

      val processed = searchResult
        .toProcessed()
        .computeMlFeatures(correction)
        .computeMlProbabilityIfEnabled()
        .tryComputeId()
        .also {
          searchResultCache[it.stateLocalId] = it
        }

      LOG.trace("Session $sessionId, State $index: Processed result $processed")

      return processed
    }

    fun getProcessedResultByIdOrNull(id: StateLocalId): SearchResultAdapter.Processed? {
      return searchResultCache[id]
    }

    fun getProcessedSearchResultById(id: StateLocalId): SearchResultAdapter.Processed {
      val cachedResult = searchResultCache[id]
      if (cachedResult != null) {
        return cachedResult
      }

      LOG.debug("Search result with ID $id not found in state $index")
      error("Search result with ID $id not found")
    }

    private fun getAllFeatures(
      contextFeatures: List<EventPair<*>>,
      elementFeatures: List<EventPair<*>>,
      contributorFeatures: List<EventPair<*>>,
    ): Map<String, Any?> {
      return (contextFeatures + elementFeatures + searchStateFeatures + contributorFeatures)
        .associate { it.field.name to it.data }
    }

    private fun getForContributor(contributorId: String): SearchEverywhereRankingModel {
      val tab = when (contributorId) {
        ActionSearchEverywhereContributor::class.java.simpleName -> SearchEverywhereTab.Actions
        FileSearchEverywhereContributor::class.java.simpleName, RecentFilesSEContributor::class.java.simpleName -> SearchEverywhereTab.Files
        ClassSearchEverywhereContributor::class.java.simpleName -> SearchEverywhereTab.Classes
        else -> throw IllegalArgumentException("Unsupported provider: $contributorId")
      }

      return modelProviderWithCache.getModel(tab)
    }

    private fun SearchResultAdapter.Processed.shouldCalculateMlWeight(): Boolean {
      return when {
        !orderByMl -> false
        isSemantic -> false  // Do not calculate machine learning weight for semantic items until the ranking models know how to treat them
        mlFeatures == null -> false
        else -> true
      }
    }


    fun getContributorFeatures(provider: SearchResultProviderAdapter, providerWeight: Int? = null): List<EventPair<*>> {
      val priority = providersInfo.providerPriorities[provider.id]
      val weight = providerWeight
                   ?: providerWeightsById[provider.id]
                   ?: (provider as? LegacyContributorAdapter)?.contributor?.sortWeight
      return SearchEverywhereContributorFeaturesProvider.getFeatures(provider, sessionStartTime, priority, weight)
    }

    private fun SearchResultAdapter.Raw.toProcessed(): SearchResultAdapter.Processed {
      return SearchResultAdapter.Processed(this, fetchRawItemIfExists(), null, null, null)
    }

    private fun SearchResultAdapter.Processed.computeMlFeatures(correction: SearchEverywhereSpellCheckResult = SearchEverywhereSpellCheckResult.NoCorrection): SearchResultAdapter.Processed {
      if (rawItem == null) return this // We cannot comput ML features when the rawItem does not exist
      val providerWeight = this.providerWeight

      val mlFeatures = SearchEverywhereElementFeaturesProvider.getFeatureProvidersForContributor(provider.id)
        .flatMap { featuresProvider ->
          featuresProvider.getElementFeatures(rawItem,
                                              sessionStartTime,
                                              query,
                                              originalWeight,
                                              providersCache,
                                              correction)
        }
        .applyIf(tab == SearchEverywhereTab.All) {
          val contributorFeatures = getContributorFeatures(provider, providerWeight)
          val mlScore = getElementMLScoreForAllTab(provider.id, cachedContextInfo.features, this, contributorFeatures)
          if (mlScore == null) {
            return@applyIf this
          }
          else {
            return@applyIf this + listOf(ML_SCORE_KEY.with(mlScore))
          }
        }

      return this.copy(mlFeatures = mlFeatures)
    }

    /**
     * Computes the ML score for an element based on its features and the contributor's model in All tab
     * where elements from different contributors are included in the search results.
     * This function should only be called for All tab, and it will throw an exception if called with a different tabId.
     * If there is no ML model for the given element, the function will return null.
     * @param contributorId The ID of the contributor that provided the element.
     * @param contextFeatures The list of context-related features.
     * @param elementFeatures The list of element-related features.
     * @param contributorFeatures The list of contributor-related features.
     */
    private fun getElementMLScoreForAllTab(contributorId: String,
                                           contextFeatures: List<EventPair<*>>,
                                           elementFeatures: List<EventPair<*>>,
                                           contributorFeatures: List<EventPair<*>>): Double? {
      check(tab == SearchEverywhereTab.All) { "This function should only be called in the All tab" }

      return try {
        val features = getAllFeatures(contextFeatures, elementFeatures, contributorFeatures)
        val model = getForContributor(contributorId)
        model.predict(features)
      }
      catch (e: IllegalArgumentException) {
        null
      }
    }

    private fun SearchResultAdapter.Processed.computeMlProbabilityIfEnabled(): SearchResultAdapter.Processed {
      if (!this.shouldCalculateMlWeight()) {
        return this
      }

      val mlFeatures = requireNotNull(mlFeatures) { "ML Probability cannot be calculated when feature list is null " }

      val features = getAllFeatures(cachedContextInfo.features,
                                    mlFeatures,
                                    getContributorFeatures(provider, this.providerWeight))
      val probability = model.predict(features)
      return this.copy(mlProbability = MlProbability(probability))
    }

    private fun SearchResultAdapter.Processed.tryComputeId(): SearchResultAdapter.Processed {
      if (rawItem == null) return this

      val id = ReadAction.compute<Int?, Nothing> { sessionWideIdProvider.getId(rawItem) }
      return if (id != null) {
          this.copy(sessionWideId = SessionWideId(id))
      }
      else {
          this
      }
    }
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
