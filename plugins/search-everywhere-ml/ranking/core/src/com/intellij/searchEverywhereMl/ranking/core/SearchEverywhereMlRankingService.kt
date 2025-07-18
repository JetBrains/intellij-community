// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.isTabWithMlRanking
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.BUFFERED_TIMESTAMP
import com.intellij.ui.components.JBList
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.ListCellRenderer

internal val searchEverywhereMlRankingService: SearchEverywhereMlRankingService?
  get() = SearchEverywhereMlService.EP_NAME.findExtensionOrFail(SearchEverywhereMlRankingService::class.java).takeIf { it.isEnabled() }

@ApiStatus.Internal
class SearchEverywhereMlRankingService : SearchEverywhereMlService {
  private val sessionIdCounter = AtomicInteger()
  private var activeSession: AtomicReference<SearchEverywhereMLSearchSession?> = AtomicReference()

  override fun isEnabled(): Boolean {
    return SearchEverywhereTab.tabsWithLogging.any { it.isTabWithMlRanking() && it.isMlRankingEnabled }
           || SearchEverywhereMlExperiment.isAllowed
  }


  internal fun getCurrentSession(): SearchEverywhereMLSearchSession? {
    if (isEnabled()) {
      return activeSession.get()
    }
    return null
  }

  override fun onSessionStarted(project: Project?, tabId: String, mixedListInfo: SearchEverywhereMixedListInfo) {
    if (isEnabled()) {
      activeSession.updateAndGet {
        SearchEverywhereMLSearchSession(project, mixedListInfo, sessionIdCounter.incrementAndGet())
      }!!.onSessionStarted(tabId)
    }
  }

  override fun createFoundElementInfo(contributor: SearchEverywhereContributor<*>,
                                      element: Any,
                                      priority: Int,
                                      correction: SearchEverywhereSpellCheckResult): SearchEverywhereFoundElementInfo {
    val foundElementInfoWithoutMl = SearchEverywhereFoundElementInfoWithMl.withoutMl(element, priority, contributor, correction)

    if (!isEnabled()) return foundElementInfoWithoutMl
    val session = getCurrentSession() ?: return foundElementInfoWithoutMl
    val state = session.getCurrentSearchState() ?: return foundElementInfoWithoutMl

    val contributorFeatures = state.getContributorFeatures(contributor)
    val elementFeatures = state.getElementFeatures(element, contributor, contributorFeatures, priority, session.cachedContextInfo, correction)

    val effectiveContributor = if (contributor is SearchEverywhereContributorWrapper) contributor.getEffectiveContributor() else contributor

    val mlWeight = if (shouldCalculateMlWeight(effectiveContributor, state, element)) {
      state.getMLWeight(session.cachedContextInfo, elementFeatures, contributorFeatures)
    } else {
      null
    }

    val elementId = ReadAction.compute<Int?, Nothing> { session.itemIdProvider.getId(element) }

    return if (isShowDiff()) {
      SearchEverywhereFoundElementInfoBeforeDiff(element, elementId, priority, contributor, mlWeight, elementFeatures, correction)
    }
    else {
      SearchEverywhereFoundElementInfoWithMl(element, elementId, priority, contributor, mlWeight, elementFeatures, correction)
    }
  }

  private fun shouldCalculateMlWeight(contributor: SearchEverywhereContributor<*>,
                                      searchState: SearchEverywhereMlSearchState,
                                      element: Any): Boolean {
    // If we're showing recently used actions (empty query) then we don't want to apply ML sorting either
    if (searchState.tab == SearchEverywhereTab.Actions && searchState.query.isEmpty()) return false

    // The element may be an ItemWithPresentation pair - we will unwrap it
    val actualElement = when (element) {
      is PSIPresentationBgRendererWrapper.ItemWithPresentation<*> -> element.item
      else -> element
    }

    // Do not calculate machine learning weight for semantic items until the ranking models know how to treat them
    if ((contributor as? SemanticSearchEverywhereContributor)?.isElementSemantic(actualElement) == true) {
      return false
    }

    return searchState.orderByMl
  }

  override fun onSearchRestart(tabId: String,
                               reason: SearchRestartReason,
                               keysTyped: Int,
                               backspacesTyped: Int,
                               searchQuery: String,
                               searchResults: List<SearchEverywhereFoundElementInfo>,
                               searchScope: ScopeDescriptor?,
                               isSearchEverywhere: Boolean) {
    if (!isEnabled()) return

    getCurrentSession()?.onSearchRestart(
      reason, tabId, keysTyped, backspacesTyped, searchQuery, searchResults.toInternalType(),
      searchScope, isSearchEverywhere
    )
  }

  override fun onItemSelected(tabId: String, indexes: IntArray, selectedItems: List<Any>,
                              searchResults: List<SearchEverywhereFoundElementInfo>,
                              query: String) {
    getCurrentSession()?.onItemSelected(indexes, selectedItems, searchResults.toInternalType())
  }

  override fun onSearchFinished(searchResults: List<SearchEverywhereFoundElementInfo>) {
    getCurrentSession()?.onSearchFinished(searchResults.toInternalType())
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

  private fun List<SearchEverywhereFoundElementInfo>.toInternalType(): List<SearchEverywhereFoundElementInfoWithMl> {
    return this.map {
      SearchEverywhereFoundElementInfoWithMl.from(it)
    }
  }
}