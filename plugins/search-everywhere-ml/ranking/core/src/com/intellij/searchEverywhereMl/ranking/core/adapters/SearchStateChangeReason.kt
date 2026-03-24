package com.intellij.searchEverywhereMl.ranking.core.adapters

import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.providers.SeTextFilter
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLSearchSession

/**
 * This class is an internal equivalent of [com.intellij.ide.actions.searcheverywhere.SearchRestartReason]
 * that is not implemented in the new (split) Search Everywhere.
 *
 * For compatibility and analytical purposes, we want to record this information,
 * so in the new Search Everywhere, we are going to infer this information.
 */
enum class SearchStateChangeReason {
  SEARCH_START,
  QUERY_CHANGE,
  QUERY_MATCHING_OPTION_CHANGE,
  SCOPE_CHANGE,
  TAB_CHANGE,
  DUMB_MODE_EXIT;

  companion object {
    /**
     * Converts values of [com.intellij.ide.actions.searcheverywhere.SearchRestartReason] (old Search Everywhere implementation)
     * to corresponding [SearchStateChangeReason] (implementation-agnostic) values.
     */
    fun fromSearchRestartReason(restartReason: SearchRestartReason): SearchStateChangeReason {
      return when (restartReason) {
        SearchRestartReason.SEARCH_STARTED -> SEARCH_START
        SearchRestartReason.TEXT_CHANGED -> QUERY_CHANGE
        SearchRestartReason.TAB_CHANGED -> TAB_CHANGE
        SearchRestartReason.SCOPE_CHANGED -> SCOPE_CHANGE
        SearchRestartReason.EXIT_DUMB_MODE -> DUMB_MODE_EXIT
        SearchRestartReason.TEXT_SEARCH_OPTION_CHANGED -> QUERY_MATCHING_OPTION_CHANGE
      }
    }

    internal fun inferFromStateChange(
      previousState: SearchEverywhereMLSearchSession.SearchState?,
      currentTabId: String,
      currentSearchParams: SeParams,
      currentIsDumbMode: Boolean,
    ): SearchStateChangeReason {
      if (previousState == null) {
        return SEARCH_START
      }

      val currentTab = SearchEverywhereTab.getById(currentTabId)
      return when {
        currentSearchParams.inputQuery != previousState.query -> QUERY_CHANGE
        currentTab != previousState.tab -> TAB_CHANGE
        previousState.isDumbMode && !currentIsDumbMode -> DUMB_MODE_EXIT
        hasQueryMatchingOptionChange(previousState.searchFilter, currentSearchParams.filter) -> QUERY_MATCHING_OPTION_CHANGE
        else -> QUERY_CHANGE
      }
    }

    private fun hasQueryMatchingOptionChange(previousSearchFilter: SeFilterState?, currentSearchFilter: SeFilterState): Boolean {
      val previousTextFilter = previousSearchFilter?.let { SeTextFilter.from(it) } ?: return false
      val currentTextFilter = SeTextFilter.from(currentSearchFilter) ?: return false

      return previousTextFilter.selectedType != currentTextFilter.selectedType ||
             previousTextFilter.isCaseSensitive != currentTextFilter.isCaseSensitive ||
             previousTextFilter.isWholeWordsOnly != currentTextFilter.isWholeWordsOnly ||
             previousTextFilter.isRegex != currentTextFilter.isRegex
    }
  }
}

fun SearchRestartReason.toSearchStateChangeReason(): SearchStateChangeReason {
  return SearchStateChangeReason.fromSearchRestartReason(this)
}
