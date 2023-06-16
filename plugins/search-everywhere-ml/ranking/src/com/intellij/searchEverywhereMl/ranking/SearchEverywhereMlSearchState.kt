// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.searchEverywhereMl.ranking.features.FeaturesProviderCache
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereContributorFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereElementFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.features.SearchEverywhereStateFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.model.SearchEverywhereModelProvider
import com.intellij.searchEverywhereMl.ranking.model.SearchEverywhereRankingModel

internal class SearchEverywhereMlSearchState(
  private val sessionStartTime: Long, val searchStartTime: Long,
  val searchIndex: Int, val searchStartReason: SearchRestartReason,
  val tabId: String, val experimentGroup: Int, val orderByMl: Boolean,
  val keysTyped: Int, val backspacesTyped: Int, private val searchQuery: String,
  private val modelProvider: SearchEverywhereModelProvider,
  private val providersCache: FeaturesProviderCache?,
  projectIsDumb: Boolean?,
  searchScope: ScopeDescriptor?,
  isSearchEverywhere: Boolean,
) {
  val searchStateFeatures = SearchEverywhereStateFeaturesProvider().getSearchStateFeatures(tabId, searchQuery, projectIsDumb,
                                                                                           searchScope, isSearchEverywhere)
  private val contributorFeaturesProvider = SearchEverywhereContributorFeaturesProvider()

  private val model: SearchEverywhereRankingModel by lazy { modelProvider.getModel(tabId) }
  fun getElementFeatures(elementId: Int?,
                         element: Any,
                         contributor: SearchEverywhereContributor<*>,
                         priority: Int,
                         mixedListInfo: SearchEverywhereMixedListInfo,
                         context: SearchEverywhereMLContextInfo): SearchEverywhereMLItemInfo {
    val features = arrayListOf<EventPair<*>>()
    val contributorId = contributor.searchProviderId
    val contributorFeatures = contributorFeaturesProvider.getFeatures(contributor, mixedListInfo)

    SearchEverywhereElementFeaturesProvider.getFeatureProvidersForContributor(contributorId).forEach { provider ->
      features.addAll(provider.getElementFeatures(element, sessionStartTime, searchQuery, priority, providersCache))
    }
    val mlScore = getElementMLScore(tabId, contributorId, context.features, features, contributorFeatures)
    SearchEverywhereElementFeaturesProvider.addMlScore(mlScore, features)

    return SearchEverywhereMLItemInfo(elementId, contributorId, features, contributorFeatures)
  }

  /**
   * Computes the ML score for an element based on its features and the contributor's model in All tab
   * where elements from different contributors are included in the search results.
   * @param tabId The ID of the search tab where this function is being called.
   * @param contributorId The ID of the contributor that provided the element.
   * @param contextFeatures The list of context-related features.
   * @param elementFeatures The list of element-related features.
   * @param contributorFeatures The list of contributor-related features.
   */
  private fun getElementMLScore(tabId: String,
                                contributorId: String,
                                contextFeatures: List<EventPair<*>>,
                                elementFeatures: List<EventPair<*>>,
                                contributorFeatures: List<EventPair<*>>): Double? {
    return try {
      if (tabId == ALL_CONTRIBUTORS_GROUP_ID) {
        val features = getAllFeatures(contextFeatures, elementFeatures, contributorFeatures)
        val model = getModelForContributor(contributorId)
        model.predict(features)
      } else {
        null
      }
    } catch (e: IllegalArgumentException) {
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

  private fun getModelForContributor(contributorId: String): SearchEverywhereRankingModel {
    val tabId = when (contributorId) {
      ActionSearchEverywhereContributor::class.java.simpleName -> ActionSearchEverywhereContributor::class.java.simpleName
      FileSearchEverywhereContributor::class.java.simpleName, RecentFilesSEContributor::class.java.simpleName ->
        FileSearchEverywhereContributor::class.java.simpleName
      ClassSearchEverywhereContributor::class.java.simpleName -> ClassSearchEverywhereContributor::class.java.simpleName
      else -> throw IllegalArgumentException("Unsupported contributorId: $contributorId")
    }

    return modelProvider.getModel(tabId)
  }

  fun getMLWeight(context: SearchEverywhereMLContextInfo,
                  itemInfo: SearchEverywhereMLItemInfo): Double {
    val features = getAllFeatures(context.features, itemInfo.features, itemInfo.contributorFeatures)
    return model.predict(features)
  }
}

internal data class SearchEverywhereMLItemInfo(val id: Int?,
                                               val contributorId: String,
                                               val features: List<EventPair<*>>,
                                               val contributorFeatures: List<EventPair<*>>)
