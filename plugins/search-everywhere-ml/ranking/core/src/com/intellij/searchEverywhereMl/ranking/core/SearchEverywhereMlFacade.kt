package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.isTabWithMlRanking
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchStateChangeReason
import java.util.concurrent.atomic.AtomicReference

internal object SearchEverywhereMlFacade {
  private val _activeSession: AtomicReference<SearchEverywhereMLSearchSession?> = AtomicReference()
  val activeSession: SearchEverywhereMLSearchSession?
    get() = _activeSession.get()

  val isMlEnabled: Boolean
    get() {
      return SearchEverywhereTab.tabsWithLogging.any { it.isTabWithMlRanking() && it.isMlRankingEnabled }
             || SearchEverywhereMlExperiment.isAllowed
    }

  fun onSessionStarted(project: Project?, tabId: String, isNewSearchEverywhere: Boolean,
                       providersInfo: SearchResultProvidersInfo = SearchResultProvidersInfo.EMPTY) {
    if (isMlEnabled) {
      val newSession = SearchEverywhereMLSearchSession.createNext(project, providersInfo).also {
        it.onSessionStarted(tabId, isNewSearchEverywhere)
      }
      _activeSession.set(newSession)
    }
  }

  fun onStateStarted(tabId: String, query: String, changeReason: SearchStateChangeReason,
                     scopeDescriptor: ScopeDescriptor?, isSearchEverywhere: Boolean,
                     searchFilter: SeFilterState? = null, isDumbMode: Boolean = false) {
    _activeSession.get()?.onStateStarted(tabId, query, changeReason, scopeDescriptor, isSearchEverywhere, searchFilter, isDumbMode)
  }

  fun onStateFinished(results: List<SearchResultAdapter.Raw>) {
    _activeSession.get()?.onStateFinished(results)
  }

  fun onResultsSelected(selectedResults: List<Pair<Int, SearchResultAdapter.Raw>>) {
    _activeSession.get()?.onItemsSelected(selectedResults)
  }

  fun notifySearchResultsUpdated() {
    _activeSession.get()?.notifySearchResultsUpdated()
  }

  fun onSessionFinished() {
    val finishedSession = _activeSession.getAndSet(null)
    finishedSession?.onSessionFinished()
  }

  fun processSearchResult(result: SearchResultAdapter.Raw): SearchResultAdapter.Processed {
    val currentSession = checkNotNull(activeSession) { "No active search session found - cannot calculate ML properties" }
    val currentState = checkNotNull(currentSession.activeState ?: currentSession.previousSearchState) { "No active search state found - cannot calculate ML properties" }

    return currentState.processSearchResult(result)
  }


  val experimentVersion: Int
    get() = SearchEverywhereMlExperiment.VERSION

  val experimentGroup: Int
    get() = SearchEverywhereMlExperiment.experimentGroup

}
