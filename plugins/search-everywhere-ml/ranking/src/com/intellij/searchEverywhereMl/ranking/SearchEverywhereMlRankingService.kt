// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.RANKING_EP_NAME
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking
import com.intellij.searchEverywhereMl.SemanticSearchEverywhereContributor
import com.intellij.searchEverywhereMl.settings.SearchEverywhereMlSettings
import com.intellij.ui.components.JBList
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.ListCellRenderer

internal val searchEverywhereMlRankingService: SearchEverywhereMlRankingService?
  get() = RANKING_EP_NAME.findExtensionOrFail(SearchEverywhereMlRankingService::class.java).takeIf { it.isEnabled() }

@ApiStatus.Internal
class SearchEverywhereMlRankingService : SearchEverywhereMlService {
  private val sessionIdCounter = AtomicInteger()
  private var activeSession: AtomicReference<SearchEverywhereMLSearchSession?> = AtomicReference()

  internal val experiment: SearchEverywhereMlExperiment = SearchEverywhereMlExperiment()

  override val shouldAllTabPrioritizeRecentFiles: Boolean
    get() = experiment.getExperimentForTab(
      SearchEverywhereTabWithMlRanking.ALL) != SearchEverywhereMlExperiment.ExperimentType.NO_RECENT_FILES_PRIORITIZATION


  override fun isEnabled(): Boolean {
    val settings = service<SearchEverywhereMlSettings>()
    return settings.isSortingByMlEnabledInAnyTab() || experiment.isAllowed
  }

  internal fun shouldUseExperimentalModel(tab: SearchEverywhereTabWithMlRanking): Boolean {
    return when (experiment.getExperimentForTab(tab)) {
      SearchEverywhereMlExperiment.ExperimentType.USE_EXPERIMENTAL_MODEL -> true
      SearchEverywhereMlExperiment.ExperimentType.NO_RECENT_FILES_PRIORITIZATION -> true
      else -> false
    }
  }

  internal fun getCurrentSession(): SearchEverywhereMLSearchSession? {
    if (isEnabled()) {
      return activeSession.get()
    }
    return null
  }

  override fun onSessionStarted(project: Project?, mixedListInfo: SearchEverywhereMixedListInfo) {
    if (isEnabled()) {
      activeSession.updateAndGet {
        SearchEverywhereMLSearchSession(project, mixedListInfo, sessionIdCounter.incrementAndGet(), FeaturesLoggingRandomisation())
      }
    }
  }

  override fun createFoundElementInfo(contributor: SearchEverywhereContributor<*>,
                                      element: Any,
                                      priority: Int): SearchEverywhereFoundElementInfo {
    val foundElementInfoWithoutMl = SearchEverywhereFoundElementInfoWithMl.withoutMl(element, priority, contributor)

    if (!isEnabled()) return foundElementInfoWithoutMl

    val session = getCurrentSession() ?: return foundElementInfoWithoutMl
    val state = session.getCurrentSearchState() ?: return foundElementInfoWithoutMl

    val tab = SearchEverywhereTabWithMlRanking.findById(state.tabId)
    tab?.let {
      if (experiment.getExperimentForTab(tab) == SearchEverywhereMlExperiment.ExperimentType.NO_ML_FEATURES)
        return foundElementInfoWithoutMl
    }


    val elementId = ReadAction.compute<Int?, Nothing> { session.itemIdProvider.getId(element) }
    val mlElementInfo = state.getElementFeatures(elementId, element, contributor, priority, session.mixedListInfo, session.cachedContextInfo)

    val mlWeight = if (shouldCalculateMlWeight(contributor, state)) {
      state.getMLWeight(session.cachedContextInfo, mlElementInfo)
    } else {
      null
    }

    return if (isShowDiff() || (contributor is SemanticSearchEverywhereContributor && contributor.isElementSemantic(element))) {
      SearchEverywhereFoundElementInfoBeforeDiff(element, priority, contributor, mlWeight, mlElementInfo.features)
    }
    else {
      SearchEverywhereFoundElementInfoWithMl(element, priority, contributor, mlWeight, mlElementInfo.features)
    }
  }

  private fun shouldCalculateMlWeight(contributor: SearchEverywhereContributor<*>, searchState: SearchEverywhereMlSearchState): Boolean {
    // Don't calculate ML weight for typo fix, as otherwise it will affect the ranking priority, which is meant to be Int.MAX_VALUE
    if (contributor is SearchEverywhereSpellingCorrectorContributor) return false
    // If we're showing recently used actions (empty query) then we don't want to apply ML sorting either
    if (searchState.tabId == ActionSearchEverywhereContributor::class.simpleName && searchState.searchQuery.isEmpty()) return false

    return searchState.orderByMl
  }

  override fun onSearchRestart(project: Project?,
                               tabId: String,
                               reason: SearchRestartReason,
                               keysTyped: Int,
                               backspacesTyped: Int,
                               searchQuery: String,
                               previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                               searchScope: ScopeDescriptor?,
                               isSearchEverywhere: Boolean) {
    if (!isEnabled()) return

    val orderByMl = shouldOrderByMlInTab(tabId, searchQuery)
    getCurrentSession()?.onSearchRestart(
      project, experiment, reason, tabId, orderByMl, keysTyped, backspacesTyped, searchQuery, mapElementsProvider(previousElementsProvider),
      searchScope, isSearchEverywhere
    )
  }

  private fun shouldOrderByMlInTab(tabId: String, searchQuery: String): Boolean {
    val tab = SearchEverywhereTabWithMlRanking.findById(tabId) ?: return false // Tab does not support ML ordering
    val settings = service<SearchEverywhereMlSettings>()

    if (tabId == SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID && searchQuery.isEmpty()) return false

    if (settings.isSortingByMlEnabledByDefault(tab)) {
      return settings.isSortingByMlEnabled(tab)
             && experiment.getExperimentForTab(tab) != SearchEverywhereMlExperiment.ExperimentType.NO_ML
             && experiment.getExperimentForTab(tab) != SearchEverywhereMlExperiment.ExperimentType.NO_ML_FEATURES
    }
    else {
      return settings.isSortingByMlEnabled(tab)
             || experiment.getExperimentForTab(tab) == SearchEverywhereMlExperiment.ExperimentType.USE_EXPERIMENTAL_MODEL
             || experiment.getExperimentForTab(tab) == SearchEverywhereMlExperiment.ExperimentType.NO_RECENT_FILES_PRIORITIZATION
    }
  }

  override fun onItemSelected(project: Project?, tabId: String, indexes: IntArray, selectedItems: List<Any>,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                              closePopup: Boolean) {
    getCurrentSession()?.onItemSelected(project, experiment, indexes, selectedItems, closePopup, mapElementsProvider(elementsProvider))
  }

  override fun onSearchFinished(project: Project?, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    getCurrentSession()?.onSearchFinished(project, experiment, mapElementsProvider(elementsProvider))
  }

  override fun notifySearchResultsUpdated() {
    getCurrentSession()?.notifySearchResultsUpdated()
  }

  override fun onDialogClose() {
    activeSession.updateAndGet { null }
  }

  private fun isShowDiff(): Boolean {
    val key = "search.everywhere.ml.show.diff"
    return Registry.`is`(key)
  }

  override fun wrapRenderer(renderer: ListCellRenderer<Any>, listModel: SearchListModel): ListCellRenderer<Any> {
    return if (isShowDiff()) {
      SearchEverywhereMLRendererWrapper(renderer, listModel)
    }
    else {
      renderer
    }

  }

  override fun buildListener(listModel: SearchListModel, resultsList: JBList<Any>, selectionTracker: SEListSelectionTracker): SearchListener? {
    return if (isShowDiff()) {
      SearchEverywhereReorderingListener(listModel, resultsList, selectionTracker)
    }
    else {
      null
    }
  }

  private fun mapElementsProvider(elementsProvider: () -> List<SearchEverywhereFoundElementInfo>): () -> List<SearchEverywhereFoundElementInfoWithMl> {
    return { ->
      elementsProvider.invoke()
        .map { SearchEverywhereFoundElementInfoWithMl.from(it) }
    }
  }
}