// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.ml.settings.SearchEverywhereMlSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMlSessionService : SearchEverywhereMlService() {
  companion object {
    internal const val RECORDER_CODE = "MLSE"

    fun getService() = EP_NAME.findExtensionOrFail(SearchEverywhereMlSessionService::class.java).takeIf { it.isEnabled() }
  }

  private val sessionIdCounter = AtomicInteger()
  private var activeSession: AtomicReference<SearchEverywhereMLSearchSession?> = AtomicReference()

  internal val experiment: SearchEverywhereMlExperiment = SearchEverywhereMlExperiment()

  override fun isEnabled(): Boolean {
    val settings = service<SearchEverywhereMlSettings>()
    return settings.isSortingByMlEnabledInAnyTab() || experiment.isAllowed
  }

  internal fun shouldUseExperimentalModel(tabId: String): Boolean {
    val tab = SearchEverywhereTabWithMl.findById(tabId) ?: return false
    return experiment.getExperimentForTab(tab) == SearchEverywhereMlExperiment.ExperimentType.USE_EXPERIMENTAL_MODEL
  }

  fun getCurrentSession(): SearchEverywhereMLSearchSession? {
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

    val elementId = session.itemIdProvider.getId(element)
    val mlFeatures = state.getElementFeatures(elementId, element, contributor, priority).features
    val mlWeight = if (state.orderByMl) state.getMLWeight(session.cachedContextInfo, mlFeatures) else null

    return SearchEverywhereFoundElementInfoWithMl(element, priority, contributor, mlWeight, mlFeatures)
  }

  override fun onSearchRestart(project: Project?,
                               tabId: String,
                               reason: SearchRestartReason,
                               keysTyped: Int,
                               backspacesTyped: Int,
                               searchQuery: String,
                               previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    if (!isEnabled()) return

    val orderByMl = shouldOrderByMlInTab(tabId)
    getCurrentSession()?.onSearchRestart(
      project, experiment, reason, tabId, orderByMl, keysTyped, backspacesTyped, searchQuery, mapElementsProvider(previousElementsProvider)
    )
  }

  private fun shouldOrderByMlInTab(tabId: String): Boolean {
    val tab = SearchEverywhereTabWithMl.findById(tabId) ?: return false // Tab does not support ML ordering
    val settings = service<SearchEverywhereMlSettings>()

    if (settings.isSortingByMlEnabledByDefault(tab)) {
      return settings.isSortingByMlEnabled(tab)
             && experiment.getExperimentForTab(tab) != SearchEverywhereMlExperiment.ExperimentType.NO_ML
    }
    else {
      return settings.isSortingByMlEnabled(tab)
             || experiment.getExperimentForTab(tab) == SearchEverywhereMlExperiment.ExperimentType.USE_EXPERIMENTAL_MODEL
    }
  }

  override fun onItemSelected(project: Project?, indexes: IntArray, selectedItems: List<Any>, closePopup: Boolean,
                              elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
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

  private fun mapElementsProvider(elementsProvider: () -> List<SearchEverywhereFoundElementInfo>): () -> List<SearchEverywhereFoundElementInfoWithMl> {
    return { ->
      elementsProvider.invoke()
        .map { SearchEverywhereFoundElementInfoWithMl.from(it) }
    }
  }
}