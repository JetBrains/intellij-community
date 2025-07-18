// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.RANKING_EP_NAME
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.isTabWithMlRanking
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.BUFFERED_TIMESTAMP
import com.intellij.searchEverywhereMl.ranking.core.features.UnexpectedElementType
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

  override fun isEnabled(): Boolean {
    return SearchEverywhereTab.allTabs.any { it.isTabWithMlRanking() && it.isMlRankingEnabled }
           || SearchEverywhereMlExperiment.isAllowed
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
        SearchEverywhereMLSearchSession(project, mixedListInfo, sessionIdCounter.incrementAndGet())
      }
    }
  }

  override fun createFoundElementInfo(contributor: SearchEverywhereContributor<*>,
                                      element: Any,
                                      priority: Int,
                                      correction: SearchEverywhereSpellCheckResult): SearchEverywhereFoundElementInfo {
    val foundElementInfoWithoutMl = SearchEverywhereFoundElementInfoWithMl.withoutMl(element, priority, contributor, correction)

    if (!isEnabled()) return foundElementInfoWithoutMl

    try {
      val session = getCurrentSession() ?: return foundElementInfoWithoutMl
      val state = session.getCurrentSearchState() ?: return foundElementInfoWithoutMl

      val elementId = ReadAction.compute<Int?, Nothing> { session.itemIdProvider.getId(element) }
      val mlElementInfo = state.getElementFeatures(elementId, element, contributor, priority, session.cachedContextInfo, correction)

      val effectiveContributor = if (contributor is SearchEverywhereContributorWrapper) contributor.getEffectiveContributor() else contributor
      val mlWeight = if (shouldCalculateMlWeight(effectiveContributor, state, element)) state.getMLWeight(session.cachedContextInfo, mlElementInfo) else null

      return if (isShowDiff()) SearchEverywhereFoundElementInfoBeforeDiff(element, priority, contributor, mlWeight, mlElementInfo.features, correction)
      else SearchEverywhereFoundElementInfoWithMl(element, priority, contributor, mlWeight, mlElementInfo.features, correction)
    } catch (ex: UnexpectedElementType) {
      thisLogger().error("Failed to compute element features", ex)
      return foundElementInfoWithoutMl
    }
  }

  private fun shouldCalculateMlWeight(contributor: SearchEverywhereContributor<*>,
                                      searchState: SearchEverywhereMlSearchState,
                                      element: Any): Boolean {
    // If we're showing recently used actions (empty query) then we don't want to apply ML sorting either
    if (searchState.tab == SearchEverywhereTab.Actions && searchState.searchQuery.isEmpty()) return false
    // Do not calculate machine learning weight for semantic items until the ranking models know how to treat them
    if ((contributor as? SemanticSearchEverywhereContributor)?.isElementSemantic(element) == true) return false

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
      project, reason, tabId, orderByMl, keysTyped, backspacesTyped, searchQuery, mapElementsProvider(previousElementsProvider),
      searchScope, isSearchEverywhere
    )
  }

  private fun shouldOrderByMlInTab(tabId: String, searchQuery: String): Boolean {
    val tab = SearchEverywhereTab.findById(tabId) ?: return false // Tab does not support ML ordering

    if (!tab.isTabWithMlRanking()) {
      return false
    }

    if (tab == SearchEverywhereTab.All && searchQuery.isEmpty()) {
      return false
    }

    return tab.isMlRankingEnabled
  }

  override fun onItemSelected(project: Project?, tabId: String, indexes: IntArray, selectedItems: List<Any>,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                              closePopup: Boolean,
                              query: String) {
    getCurrentSession()?.onItemSelected(project, indexes, selectedItems, closePopup, mapElementsProvider(elementsProvider))
  }

  override fun onSearchFinished(project: Project?, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    getCurrentSession()?.onSearchFinished(project, mapElementsProvider(elementsProvider))
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

  override fun getExperimentVersion(): Int = SearchEverywhereMlExperiment.VERSION

  override fun getExperimentGroup(): Int = SearchEverywhereMlExperiment.experimentGroup

  override fun addBufferedTimestamp(item: SearchEverywhereFoundElementInfo, timestamp: Long) {
    (item as? SearchEverywhereFoundElementInfoWithMl)?.let {
      val session = getCurrentSession() ?: return
      session.getCurrentSearchState()?.apply {
        item.addMlFeature(BUFFERED_TIMESTAMP.with(timestamp))
      }
    }
  }

  private fun mapElementsProvider(elementsProvider: () -> List<SearchEverywhereFoundElementInfo>): () -> List<SearchEverywhereFoundElementInfoWithMl> {
    return { ->
      elementsProvider.invoke()
        .map {
          SearchEverywhereFoundElementInfoWithMl.from(it) }
    }
  }
}