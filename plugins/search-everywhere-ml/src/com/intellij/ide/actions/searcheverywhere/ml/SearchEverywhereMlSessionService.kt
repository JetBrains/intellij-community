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

  private val experiment: SearchEverywhereMlExperiment = SearchEverywhereMlExperiment()

  override fun isEnabled(): Boolean {
    val settings = service<SearchEverywhereMlSettings>()
    return settings.isSortingByMlEnabledInAnyTab() || experiment.isAllowed
  }

  override fun shouldOrderByMl(): Boolean {
    val state = getCurrentSession()?.getCurrentSearchState()
    return state?.orderByMl ?: false
  }

  internal fun shouldUseExperimentalModel(tabId: String): Boolean {
    val tab = SearchEverywhereTabWithMl.findById(tabId) ?: return false
    return experiment.getExperimentForTab(tab) == SearchEverywhereMlExperiment.ExperimentType.USE_EXPERIMENTAL_MODEL
  }

  override fun getMlWeight(contributor: SearchEverywhereContributor<*>, element: Any, matchingDegree: Int): Double {
    val session = getCurrentSession() ?: return -1.0
    return session.getMLWeight(contributor, element, matchingDegree)
  }

  fun getCurrentSession(): SearchEverywhereMLSearchSession? {
    if (isEnabled()) {
      return activeSession.get()
    }
    return null
  }

  override fun onSessionStarted(project: Project?, mixedListInfo: SearchEverywhereMixedListInfo) {
    if (isEnabled()) {
      activeSession.updateAndGet { SearchEverywhereMLSearchSession(project, mixedListInfo, sessionIdCounter.incrementAndGet()) }
    }
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
      project, experiment, reason, tabId, orderByMl, keysTyped, backspacesTyped, searchQuery, previousElementsProvider
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
    getCurrentSession()?.onItemSelected(project, experiment, indexes, selectedItems, closePopup, elementsProvider)
  }

  override fun onSearchFinished(project: Project?, elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    getCurrentSession()?.onSearchFinished(project, experiment, elementsProvider)
  }

  override fun notifySearchResultsUpdated() {
    getCurrentSession()?.notifySearchResultsUpdated()
  }

  override fun onDialogClose() {
    activeSession.updateAndGet { null }
  }
}