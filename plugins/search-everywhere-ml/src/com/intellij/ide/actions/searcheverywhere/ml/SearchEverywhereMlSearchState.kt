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
  val sessionStartTime: Long, val searchStartTime: Long,
  val searchIndex: Int, val searchStartReason: SearchRestartReason, val tabId: String,
  val keysTyped: Int, val backspacesTyped: Int, private val searchQuery: String,
  private val modelProvider: SearchEverywhereModelProvider,
  private val providersCache: FeaturesProviderCache?
) {
  private val cachedElementsInfo: MutableMap<Int, SearchEverywhereMLItemInfo> = hashMapOf()
  private val cachedMLWeight: MutableMap<Int, Double> = hashMapOf()

  val searchStateFeatures = SearchEverywhereStateFeaturesProvider().getSearchStateFeatures(tabId, searchQuery)

  private val model: SearchEverywhereRankingModel by lazy {
    SearchEverywhereRankingModel(modelProvider.getModel(tabId))
  }

  @Synchronized
  fun getElementFeatures(elementId: Int,
                         element: Any,
                         contributor: SearchEverywhereContributor<*>,
                         priority: Int): SearchEverywhereMLItemInfo {
    return cachedElementsInfo.computeIfAbsent(elementId) {
      val features = arrayListOf<EventPair<*>>()
      val contributorId = contributor.searchProviderId
      SearchEverywhereElementFeaturesProvider.getFeatureProvidersForContributor(contributorId).forEach { provider ->
        features.addAll(provider.getElementFeatures(element, sessionStartTime, searchQuery, priority, providersCache))
      }

      return@computeIfAbsent SearchEverywhereMLItemInfo(elementId, contributorId, features)
    }
  }

  @Synchronized
  fun getMLWeightIfDefined(elementId: Int): Double? {
    return cachedMLWeight[elementId]
  }

  @Synchronized
  fun getMLWeight(elementId: Int,
                  element: Any,
                  contributor: SearchEverywhereContributor<*>,
                  context: SearchEverywhereMLContextInfo,
                  priority: Int): Double {
    return cachedMLWeight.computeIfAbsent(elementId) {
      val features = ArrayList<EventPair<*>>()
      features.addAll(context.features)
      features.addAll(getElementFeatures(elementId, element, contributor, priority).features)
      features.addAll(searchStateFeatures)
      model.predict(features.associate { it.field.name to it.data })
    }
  }
}

internal data class SearchEverywhereMLItemInfo(val id: Int, val contributorId: String, val features: List<EventPair<*>>) {
  fun featuresAsMap(): Map<String, Any> = features.mapNotNull {
    val data = it.data
    if (data == null) null else it.field.name to data
  }.toMap()
}