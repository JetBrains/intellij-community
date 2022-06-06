// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.ml.settings.SearchEverywhereMlSettings
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMlSessionService : SearchEverywhereMlService() {
  companion object {
    val ML_FEATURES_KEY = Key.create<List<EventPair<*>>>("se-ml-features")
    val ML_WEIGHT_KEY = Key.create<Double>("se-ml-weight")

    private const val MAX_ELEMENT_WEIGHT = 10_000

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

  override fun createFoundElementInfo(contributor: SearchEverywhereContributor<*>,
                                      element: Any,
                                      priority: Int): SearchEverywhereFoundElementInfo {
    val foundElementInfoWithoutMl = SearchEverywhereFoundElementInfo(element, priority, contributor)

    if (!isEnabled()) return foundElementInfoWithoutMl

    val session = getCurrentSession() ?: return foundElementInfoWithoutMl
    val state = session.getCurrentSearchState() ?: return foundElementInfoWithoutMl

    val elementId = session.itemIdProvider.getId(element)
    val mlFeatures = state.getElementFeatures(elementId, element, contributor, priority).features

    // We can only compute the ML weight if we are in a tab which has a ML model
    val mlWeight = state.takeIf { SearchEverywhereTabWithMl.findById(it.tabId) != null }
      ?.getMLWeight(elementId, element, contributor, session.cachedContextInfo, priority)

    val foundElementInfo = if (shouldOrderByMl() && mlWeight != null) {
      val priorityWithMl = getPriorityWithMl(element, mlWeight, priority)
      SearchEverywhereFoundElementInfo(element, priorityWithMl, contributor)
    }
    else {
      foundElementInfoWithoutMl
    }

    foundElementInfo.putUserData(ML_FEATURES_KEY, mlFeatures)
    foundElementInfo.putUserData(ML_WEIGHT_KEY, mlWeight)

    return foundElementInfo
  }

  private fun getPriorityWithMl(element: Any, mlWeight: Double, priority: Int): Int {
    val weight = if (element is GotoActionModel.MatchedValue && element.type == GotoActionModel.MatchedValueType.ABBREVIATION) 1.0 else mlWeight
    return (weight * MAX_ELEMENT_WEIGHT).toInt() * 100_000 + priority
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