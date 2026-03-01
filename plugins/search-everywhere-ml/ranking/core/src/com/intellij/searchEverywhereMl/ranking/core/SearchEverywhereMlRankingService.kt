// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlService
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.openapi.diagnostic.ThrottledLogger
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.searchEverywhereMl.ranking.core.adapters.toSearchStateChangeReason
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import java.util.concurrent.TimeUnit.MINUTES


@ApiStatus.Internal
class SearchEverywhereMlRankingService : SearchEverywhereMlService {
  companion object {
    internal val LOG = logger<SearchEverywhereMlRankingService>()
  }

  override fun isEnabled(): Boolean {
    return SearchEverywhereMlFacade.isMlEnabled
  }

  override fun onSessionStarted(project: Project?, tabId: String, mixedListInfo: SearchEverywhereMixedListInfo) {
    SearchEverywhereMlFacade.onSessionStarted(project, tabId, isNewSearchEverywhere = false,
                                              providersInfo = SearchResultProvidersInfo.fromMixedListInfo(mixedListInfo))
  }

  override fun createFoundElementInfo(contributor: SearchEverywhereContributor<*>,
                                      element: Any,
                                      priority: Int,
                                      correction: SearchEverywhereSpellCheckResult): SearchEverywhereFoundElementInfo {
    val elementInfo = SearchEverywhereFoundElementInfo(UUID.randomUUID().toString(), element, priority, contributor, correction)
    val searchResultAdapter = SearchResultAdapter.createAdapterFor(elementInfo)
    val processedSearchResult = try {
      SearchEverywhereMlFacade.processSearchResult(searchResultAdapter)
    }
    catch (e: IllegalStateException) {
      THROTTLED_LOG.warn(
        "Missing active Search Everywhere ML session/state. Falling back to default ranking for contributor ${contributor.searchProviderId}",
        e
      )
      return elementInfo
    }

    if (processedSearchResult.mlProbability != null) {
      return elementInfo.withPriority(processedSearchResult.finalPriority)
    } else {
      return elementInfo
    }
  }

  override fun onStateStarted(
    tabId: String,
    reason: SearchRestartReason,
    searchQuery: String,
    searchScope: ScopeDescriptor?,
    isSearchEverywhere: Boolean,
  ) {
    val changeReason = if (reason == SearchRestartReason.SCOPE_CHANGED) {
      inferReasonForOldSE(tabId, searchQuery)
    }
    else {
      reason.toSearchStateChangeReason()
    }
    SearchEverywhereMlFacade.onStateStarted(tabId, searchQuery, changeReason, searchScope, isSearchEverywhere)
  }

  private fun inferReasonForOldSE(tabId: String, searchQuery: String): SearchStateChangeReason {
    val activeSession = SearchEverywhereMlFacade.activeSession ?: return SearchStateChangeReason.SCOPE_CHANGE
    val previousState = activeSession.activeState ?: activeSession.previousSearchState
                        ?: return SearchStateChangeReason.SEARCH_START

    val tab = SearchEverywhereTab.getById(tabId)
    return when {
      searchQuery != previousState.query -> SearchStateChangeReason.QUERY_CHANGE
      tab != previousState.tab -> SearchStateChangeReason.TAB_CHANGE
      else -> SearchStateChangeReason.QUERY_CHANGE // In old SE, scope-only changes are auto-escalation
    }
  }

  override fun onStateFinished(results: List<SearchEverywhereFoundElementInfo>) {
    SearchEverywhereMlFacade.onStateFinished(results.toAdapter())
  }

  override fun onItemSelected(tabId: String, indexes: IntArray, selectedItems: List<Any>,
                              searchResults: List<SearchEverywhereFoundElementInfo>,
                              query: String) {
    val selectedItems = searchResults
      .mapIndexed { index, info ->  index to SearchResultAdapter.createAdapterFor(info) }
      .slice(indexes.toList())

    SearchEverywhereMlFacade.onResultsSelected(selectedItems)
  }

  override fun onSessionFinished() {
    SearchEverywhereMlFacade.onSessionFinished()
  }

  override fun notifySearchResultsUpdated() {
    SearchEverywhereMlFacade.notifySearchResultsUpdated()
  }

  override fun onDialogClose() {
    SearchEverywhereMlFacade.onSessionFinished()
  }

  override fun getExperimentVersion(): Int {
    return SearchEverywhereMlFacade.experimentVersion
  }

  override fun getExperimentGroup(): Int {
    return SearchEverywhereMlFacade.experimentGroup
  }

  private fun SearchEverywhereFoundElementInfo.withPriority(priority: Int): SearchEverywhereFoundElementInfo {
    return SearchEverywhereFoundElementInfo(uuid, element, priority, contributor, correction)
  }

  private fun List<SearchEverywhereFoundElementInfo>.toAdapter(): List<SearchResultAdapter.Raw> {
    return this.map {
      SearchResultAdapter.createAdapterFor(it)
    }
  }
}

private val THROTTLED_LOG = ThrottledLogger(SearchEverywhereMlRankingService.LOG, MINUTES.toMillis(1))