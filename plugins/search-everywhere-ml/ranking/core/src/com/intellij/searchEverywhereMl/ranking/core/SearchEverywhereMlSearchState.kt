// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.ranking.core.features.*
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.ML_SCORE_KEY
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereModelProvider
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereRankingModel

internal class SearchEverywhereMlSearchState(
  val sessionStartTime: Long, val searchStartTime: Long,
  val searchIndex: Int, val searchStartReason: SearchRestartReason,
  val tab: SearchEverywhereTab, val experimentGroup: Int, val orderByMl: Boolean,
  val keysTyped: Int, val backspacesTyped: Int, val searchQuery: String,
  private val modelProvider: SearchEverywhereModelProvider,
  private val providersCache: FeaturesProviderCache?,
  private val mixedListInfo: SearchEverywhereMixedListInfo,
  project: Project?,
  searchScope: ScopeDescriptor?,
  isSearchEverywhere: Boolean,
) {
  val searchStateFeatures = SearchEverywhereStateFeaturesProvider().getSearchStateFeatures(project, tab, searchQuery,
                                                                                           searchScope, isSearchEverywhere)
  private val contributorFeaturesProvider = SearchEverywhereContributorFeaturesProvider()

  private val model: SearchEverywhereRankingModel by lazy { modelProvider.getModel(tab as SearchEverywhereTab.TabWithMlRanking) }
  fun getElementFeatures(elementId: Int?,
                         element: Any,
                         contributor: SearchEverywhereContributor<*>,
                         priority: Int,
                         context: SearchEverywhereMLContextInfo): SearchEverywhereMLItemInfo {
    val features = arrayListOf<EventPair<*>>()
    val contributorId = contributor.searchProviderId
    val contributorFeatures = getContributorFeatures(contributor)

    SearchEverywhereElementFeaturesProvider.getFeatureProvidersForContributor(contributorId).forEach { provider ->
      features.addAll(provider.getElementFeatures(element, sessionStartTime, searchQuery, priority, providersCache))
    }

    if (tab == SearchEverywhereTab.All) {
      val mlScore = getElementMLScoreForAllTab(contributorId, context.features, features, contributorFeatures)
      features.putIfValueNotNull(ML_SCORE_KEY, mlScore)
    }

    return SearchEverywhereMLItemInfo(elementId, contributorId, features, contributorFeatures)
  }

  /**
   * Computes the ML score for an element based on its features and the contributor's model in All tab
   * where elements from different contributors are included in the search results.
   * This function should only be called for All tab, and it will throw an exception if called with a different tabId.
   * If there is no ML model for the given element, the function will return null.
   * @param contributorId The ID of the contributor that provided the element.
   * @param contextFeatures The list of context-related features.
   * @param elementFeatures The list of element-related features.
   * @param contributorFeatures The list of contributor-related features.
   */
  private fun getElementMLScoreForAllTab(contributorId: String,
                                         contextFeatures: List<EventPair<*>>,
                                         elementFeatures: List<EventPair<*>>,
                                         contributorFeatures: List<EventPair<*>>): Double? {
    check(tab == SearchEverywhereTab.All) { "This function should only be called in the All tab" }

    return try {
      val features = getAllFeatures(contextFeatures, elementFeatures, contributorFeatures)
      val model = getForContributor(contributorId)
      model.predict(features)
    }
    catch (e: IllegalArgumentException) {
      null
    }
  }

  private fun getAllFeatures(
    contextFeatures: List<EventPair<*>>,
    elementFeatures: List<EventPair<*>>,
    contributorFeatures: List<EventPair<*>>,
  ): Map<String, Any?> {
    return (contextFeatures + elementFeatures + searchStateFeatures + contributorFeatures)
      .associate { it.field.name to it.data }
  }

  private fun getForContributor(contributorId: String): SearchEverywhereRankingModel {
    val tab = when (contributorId) {
      ActionSearchEverywhereContributor::class.java.simpleName -> SearchEverywhereTab.Actions
      FileSearchEverywhereContributor::class.java.simpleName, RecentFilesSEContributor::class.java.simpleName -> SearchEverywhereTab.Files
      ClassSearchEverywhereContributor::class.java.simpleName -> SearchEverywhereTab.Classes
      else -> throw IllegalArgumentException("Unsupported contributorId: $contributorId")
    }

    return modelProvider.getModel(tab)
  }

  fun getMLWeight(context: SearchEverywhereMLContextInfo,
                  itemInfo: SearchEverywhereMLItemInfo): Double {
    val features = getAllFeatures(context.features, itemInfo.features, itemInfo.contributorFeatures)
    return model.predict(features)
  }

  fun getContributorFeatures(contributor: SearchEverywhereContributor<*>): List<EventPair<*>> {
    return contributorFeaturesProvider.getFeatures(contributor, mixedListInfo, sessionStartTime)
  }
}

internal data class SearchEverywhereMLItemInfo(val id: Int?,
                                               val contributorId: String,
                                               val features: List<EventPair<*>>,
                                               val contributorFeatures: List<EventPair<*>>)
