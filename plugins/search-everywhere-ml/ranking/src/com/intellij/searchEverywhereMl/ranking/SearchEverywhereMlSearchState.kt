// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
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

  private val model: SearchEverywhereRankingModel by lazy {
    SearchEverywhereRankingModel(modelProvider.getModel(tabId))
  }

  fun getElementFeatures(elementId: Int?,
                         element: Any,
                         contributor: SearchEverywhereContributor<*>,
                         priority: Int,
                         mixedListInfo: SearchEverywhereMixedListInfo): SearchEverywhereMLItemInfo {
    val features = arrayListOf<EventPair<*>>()
    val contributorId = contributor.searchProviderId
    val contributorFeatures = contributorFeaturesProvider.getFeatures(contributor, mixedListInfo)
    SearchEverywhereElementFeaturesProvider.getFeatureProvidersForContributor(contributorId).forEach { provider ->
      features.addAll(provider.getElementFeatures(element, sessionStartTime, searchQuery, priority, providersCache))
    }

    return SearchEverywhereMLItemInfo(elementId, contributorId, features, contributorFeatures)
  }

  fun getMLWeight(context: SearchEverywhereMLContextInfo,
                  itemInfo: SearchEverywhereMLItemInfo): Double {
    val features = (context.features + itemInfo.features + searchStateFeatures + itemInfo.contributorFeatures)
      .associate { it.field.name to it.data }
    return model.predict(features)
  }
}

internal data class SearchEverywhereMLItemInfo(val id: Int?,
                                               val contributorId: String,
                                               val features: List<EventPair<*>>,
                                               val contributorFeatures: List<EventPair<*>>)
