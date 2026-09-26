package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.providers.SeTextFilter
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class SplitSeMlServiceInferReasonTest {
  private val actionsTabId = SearchEverywhereTab.Actions.tabId
  private val allTabId = SearchEverywhereTab.All.tabId

  private fun previousState(
    tabId: String,
    query: String = "query",
    filter: SeFilterState = SeFilterState.Empty,
    isDumbMode: Boolean = false,
  ): SearchEverywhereMLSearchSession.SearchState {
    val session = SearchEverywhereMLSearchSession.createNext(project = null, providersInfo = SearchResultProvidersInfo.EMPTY)
    session.onStateStarted(
      tabId = tabId,
      query = query,
      reason = SearchStateChangeReason.SEARCH_START,
      scopeDescriptor = null,
      isSearchEverywhere = false,
      searchFilter = filter,
      isDumbMode = isDumbMode,
    )
    return checkNotNull(session.activeState)
  }

  @Test
  fun `null previous state returns SEARCH_START`() {
    val reason = SearchStateChangeReason.inferFromStateChange(
      previousState = null,
      currentTabId = actionsTabId,
      currentSearchParams = SeParams(inputQuery = "test", filter = SeFilterState.Empty),
      currentIsDumbMode = false,
    )
    assertEquals(SearchStateChangeReason.SEARCH_START, reason)
  }

  @Test
  fun `different query returns QUERY_CHANGE`() {
    val reason = SearchStateChangeReason.inferFromStateChange(
      previousState = previousState(tabId = actionsTabId, query = "old query"),
      currentTabId = actionsTabId,
      currentSearchParams = SeParams(inputQuery = "new query", filter = SeFilterState.Empty),
      currentIsDumbMode = false,
    )
    assertEquals(SearchStateChangeReason.QUERY_CHANGE, reason)
  }

  @Test
  fun `same query different tab returns TAB_CHANGE`() {
    val reason = SearchStateChangeReason.inferFromStateChange(
      previousState = previousState(tabId = actionsTabId),
      currentTabId = allTabId,
      currentSearchParams = SeParams(inputQuery = "query", filter = SeFilterState.Empty),
      currentIsDumbMode = false,
    )
    assertEquals(SearchStateChangeReason.TAB_CHANGE, reason)
  }

  @Test
  fun `dumb mode exit returns DUMB_MODE_EXIT`() {
    val reason = SearchStateChangeReason.inferFromStateChange(
      previousState = previousState(tabId = actionsTabId, isDumbMode = true),
      currentTabId = actionsTabId,
      currentSearchParams = SeParams(inputQuery = "query", filter = SeFilterState.Empty),
      currentIsDumbMode = false,
    )
    assertEquals(SearchStateChangeReason.DUMB_MODE_EXIT, reason)
  }

  @Test
  fun `text filter options change returns QUERY_MATCHING_OPTION_CHANGE`() {
    val previousFilter = SeTextFilter(
      selectedScopeId = "scope",
      selectedType = null,
      isCaseSensitive = false,
      isWholeWordsOnly = false,
      isRegex = false,
    ).toState()
    val currentFilter = SeTextFilter(
      selectedScopeId = "scope",
      selectedType = null,
      isCaseSensitive = true,
      isWholeWordsOnly = false,
      isRegex = false,
    ).toState()

    val reason = SearchStateChangeReason.inferFromStateChange(
      previousState = previousState(tabId = actionsTabId, filter = previousFilter),
      currentTabId = actionsTabId,
      currentSearchParams = SeParams(inputQuery = "query", filter = currentFilter),
      currentIsDumbMode = false,
    )
    assertEquals(SearchStateChangeReason.QUERY_MATCHING_OPTION_CHANGE, reason)
  }

  @Test
  fun `scope-only change returns QUERY_CHANGE fallback`() {
    val previousFilter = SeTextFilter(
      selectedScopeId = "scope-a",
      selectedType = null,
      isCaseSensitive = false,
      isWholeWordsOnly = false,
      isRegex = false,
    ).toState()
    val currentFilter = SeTextFilter(
      selectedScopeId = "scope-b",
      selectedType = null,
      isCaseSensitive = false,
      isWholeWordsOnly = false,
      isRegex = false,
    ).toState()

    val reason = SearchStateChangeReason.inferFromStateChange(
      previousState = previousState(tabId = actionsTabId, filter = previousFilter),
      currentTabId = actionsTabId,
      currentSearchParams = SeParams(inputQuery = "query", filter = currentFilter),
      currentIsDumbMode = false,
    )
    assertEquals(SearchStateChangeReason.QUERY_CHANGE, reason)
  }
}
