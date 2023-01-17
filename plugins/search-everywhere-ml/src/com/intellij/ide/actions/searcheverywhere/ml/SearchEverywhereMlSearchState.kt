// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.actions.searcheverywhere.ml.features.FeaturesProviderCache
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereStateFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereModelProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereRankingModel
import com.intellij.internal.statistic.eventLog.events.EventPair

internal class SearchEverywhereMlSearchState(
  private val sessionStartTime: Long, val searchStartTime: Long,
  val searchIndex: Int, val searchStartReason: SearchRestartReason,
  val tabId: String, val experimentGroup: Int, val orderByMl: Boolean,
  val keysTyped: Int, val backspacesTyped: Int, private val searchQuery: String,
  private val modelProvider: SearchEverywhereModelProvider,
  private val providersCache: FeaturesProviderCache?
) {
  val searchStateFeatures = SearchEverywhereStateFeaturesProvider().getSearchStateFeatures(tabId, searchQuery)

  private val model: SearchEverywhereRankingModel by lazy {
    SearchEverywhereRankingModel(modelProvider.getModel(tabId))
  }

  fun getElementFeatures(elementId: Int?,
                         element: Any,
                         contributor: SearchEverywhereContributor<*>,
                         priority: Int): SearchEverywhereMLItemInfo {
    val features = arrayListOf<EventPair<*>>()
    val contributorId = contributor.searchProviderId
    SearchEverywhereElementFeaturesProvider.getFeatureProvidersForContributor(contributorId).forEach { provider ->
      features.addAll(provider.getElementFeatures(element, sessionStartTime, searchQuery, priority, providersCache))
    }

    return SearchEverywhereMLItemInfo(elementId, contributorId, features)
  }

  fun getMLWeight(context: SearchEverywhereMLContextInfo,
                  elementFeatures: List<EventPair<*>>): Double {
    val features = (context.features + elementFeatures + searchStateFeatures).associate { it.field.name to it.data }
    return model.predict(features)
  }
}

internal data class SearchEverywhereMLItemInfo(val id: Int?, val contributorId: String, val features: List<EventPair<*>>) {
  fun featuresAsMap(): Map<String, Any> = features.mapNotNull {
    val data = it.data
    if (data == null) null else it.field.name to data
  }.toMap()
}