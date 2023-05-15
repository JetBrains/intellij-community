// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.common.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.common.SearchEverywhereTabWithMlRanking
import com.intellij.searchEverywhereMl.common.settings.SearchEverywhereMlSettings
import com.intellij.ui.components.JBList
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.swing.ListCellRenderer


@ApiStatus.Internal
class SearchEverywhereMlServiceImpl : SearchEverywhereMlService() {
  companion object {
    internal const val RECORDER_CODE = "MLSE"

    fun getService() = EP_NAME.findExtensionOrFail(SearchEverywhereMlServiceImpl::class.java).takeIf { it.isEnabled() }
  }

  private val sessionIdCounter = AtomicInteger()
  private var activeSession: AtomicReference<SearchEverywhereMLSearchSession?> = AtomicReference()

  internal val experiment: SearchEverywhereMlExperiment = SearchEverywhereMlExperiment()

  override fun isEnabled(): Boolean {
    val settings = service<SearchEverywhereMlSettings>()
    return settings.isSortingByMlEnabledInAnyTab() || experiment.isAllowed
  }

  internal fun shouldUseExperimentalModel(tabId: String): Boolean {
    val tab = SearchEverywhereTabWithMlRanking.findById(tabId) ?: return false
    return experiment.getExperimentForTab(tab) == SearchEverywhereMlExperiment.ExperimentType.USE_EXPERIMENTAL_MODEL
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


    val elementId = session.itemIdProvider.getId(element)
    val mlElementInfo = state.getElementFeatures(elementId, element, contributor, priority, session.mixedListInfo)
    val mlWeight = if (state.orderByMl) state.getMLWeight(session.cachedContextInfo, mlElementInfo) else null

    return if (isShowDiff()) {
      SearchEverywhereFoundElementInfoBeforeDiff(element, priority, contributor, mlWeight, mlElementInfo.features)
    }
    else {
      SearchEverywhereFoundElementInfoWithMl(element, priority, contributor, mlWeight, mlElementInfo.features)
    }
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