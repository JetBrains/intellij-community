// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereContextFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class SearchEverywhereMLSearchSession(project: Project?, private val sessionId: Int) {
  private val itemIdProvider = SearchEverywhereMlItemIdProvider()
  private val sessionStartTime: Long = System.currentTimeMillis()
  private val providersCaches: Map<Class<out SearchEverywhereElementFeaturesProvider>, Any>

  // context features are calculated once per Search Everywhere session
  private val cachedContextInfo: SearchEverywhereMLContextInfo = SearchEverywhereMLContextInfo(project)

  // search state is updated on each typing, tab or setting change
  // element features & ML score are also re-calculated on each typing because some of them might change, e.g. matching degree
  private val currentSearchState: AtomicReference<SearchEverywhereMlSearchState?> = AtomicReference<SearchEverywhereMlSearchState?>()
  private val logger: SearchEverywhereMLStatisticsCollector = SearchEverywhereMLStatisticsCollector()

  init {
    providersCaches = SearchEverywhereElementFeaturesProvider.getFeatureProviders()
      .associate { it::class.java to it.getDataToCache(project) }
      .mapNotNull { it.value?.let { value -> it.key to value } }
      .toMap()
  }

  fun onSearchRestart(project: Project?, previousElementsProvider: () -> List<SearchEverywhereFoundElementInfo>,
                      reason: SearchRestartReason,
                      tabId: String,
                      keysTyped: Int,
                      backspacesTyped: Int,
                      queryLength: Int) {
    val prevState = currentSearchState.getAndUpdate { prevState ->
      val startTime = System.currentTimeMillis()
      val searchReason = if (prevState == null) SearchRestartReason.SEARCH_STARTED else reason
      val nextSearchIndex = (prevState?.searchIndex ?: 0) + 1
      SearchEverywhereMlSearchState(sessionStartTime, startTime, nextSearchIndex, searchReason, tabId, keysTyped, backspacesTyped,
                                    queryLength, providersCaches)
    }

    if (prevState != null && isMLSupportedTab(tabId)) {
      logger.onSearchRestarted(project, sessionId, prevState.searchIndex, itemIdProvider, cachedContextInfo, prevState,
                               previousElementsProvider)
    }
  }

  fun onItemSelected(project: Project?, experimentStrategy: SearchEverywhereMlExperiment,
                     indexes: IntArray, closePopup: Boolean,
                     elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val state = currentSearchState.get()
    if (state != null && isMLSupportedTab(state.tabId)) {
      val orderByMl = orderedByMl(experimentStrategy, state.tabId)
      logger.onItemSelected(
        project, sessionId, state.searchIndex,
        experimentStrategy.experimentGroup, orderByMl,
        itemIdProvider, cachedContextInfo, state,
        indexes, closePopup, elementsProvider
      )
    }
  }

  fun onSearchFinished(project: Project?, experimentStrategy: SearchEverywhereMlExperiment,
                       elementsProvider: () -> List<SearchEverywhereFoundElementInfo>) {
    val state = currentSearchState.get()
    if (state != null && isMLSupportedTab(state.tabId)) {
      val orderByMl = orderedByMl(experimentStrategy, state.tabId)
      logger.onSearchFinished(
        project, sessionId, state.searchIndex,
        experimentStrategy.experimentGroup, orderByMl,
        itemIdProvider, cachedContextInfo, state,
        elementsProvider
      )
    }
  }

  fun getMLWeight(contributor: SearchEverywhereContributor<*>, element: GotoActionModel.MatchedValue): Double {
    val state = currentSearchState.get()
    if (state != null && isActionsTab(state.tabId)) {
      val id = itemIdProvider.getId(element)
      return state.getMLWeight(id, element, contributor, cachedContextInfo, element.matchingDegree)
    }
    return -1.0
  }

  private fun orderedByMl(experimentStrategy: SearchEverywhereMlExperiment, tabId: String): Boolean {
    return isMLSupportedTab(tabId) && experimentStrategy.shouldOrderByMl()
  }

  private fun isMLSupportedTab(tabId: String): Boolean {
    return isFilesTab(tabId) || isActionsTab(tabId)
  }

  private fun isFilesTab(tabId: String) = FileSearchEverywhereContributor::class.java.simpleName == tabId

  private fun isActionsTab(tabId: String) = ActionSearchEverywhereContributor::class.java.simpleName == tabId
}

class SearchEverywhereMlItemIdProvider {
  private var idCounter = AtomicInteger(1)
  private val itemToId = ContainerUtil.createWeakMap<Any, Int>()

  @Synchronized
  fun getId(element: Any): Int {
    val key = when (element) {
      is GotoActionModel.MatchedValue -> getActionKey(element)
      is PSIPresentationBgRendererWrapper.PsiItemWithPresentation -> {
        (element.item as? PsiFileSystemItem)?.virtualFile ?: throw IllegalArgumentException("Illegal argument type ${element.javaClass.name}")
      }
      is PsiFileSystemItem -> element.virtualFile
      else -> throw IllegalArgumentException("Illegal argument type ${element.javaClass.name}")
    }
    return itemToId.computeIfAbsent(key) { idCounter.getAndIncrement() }
  }

  private fun getActionKey(element: GotoActionModel.MatchedValue): Any {
    if (element.value is GotoActionModel.ActionWrapper) {
      return (element.value as GotoActionModel.ActionWrapper).action
    } else {
      return element.value
    }
  }
}

internal class SearchEverywhereMLContextInfo(project: Project?) {
  val features: Map<String, Any> by lazy {
    val featuresProvider = SearchEverywhereContextFeaturesProvider()
    return@lazy featuresProvider.getContextFeatures(project)
  }
}