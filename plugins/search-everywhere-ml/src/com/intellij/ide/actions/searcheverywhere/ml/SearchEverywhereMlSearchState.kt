// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchRestartReason
import com.intellij.ide.actions.searcheverywhere.ml.features.SearchEverywhereElementFeaturesProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereMLRankingModelProvider
import com.intellij.ide.actions.searcheverywhere.ml.model.SearchEverywhereRankingModel

internal class SearchEverywhereMlSearchState(
  val sessionStartTime: Long, val searchStartTime: Long,
  val searchIndex: Int, val searchStartReason: SearchRestartReason, val tabId: String,
  val keysTyped: Int, val backspacesTyped: Int, private val searchQuery: String,
  private val providersCaches: Map<Class<out SearchEverywhereElementFeaturesProvider>, Any>
) {
  private val cachedElementsInfo: MutableMap<Int, SearchEverywhereMLItemInfo> = hashMapOf()
  private val cachedMLWeight: MutableMap<Int, Double> = hashMapOf()

  private val model: SearchEverywhereRankingModel by lazy {
    val provider = SearchEverywhereMLRankingModelProvider.getForTab(tabId)
    SearchEverywhereRankingModel(provider)
  }

  @Synchronized
  fun getElementFeatures(elementId: Int,
                         element: Any,
                         contributor: SearchEverywhereContributor<*>,
                         priority: Int): SearchEverywhereMLItemInfo {
    return cachedElementsInfo.computeIfAbsent(elementId) {
      val features = mutableMapOf<String, Any>()
      val contributorId = contributor.searchProviderId
      SearchEverywhereElementFeaturesProvider.getFeatureProvidersForContributor(contributorId).forEach { provider ->
        val cache = providersCaches[provider::class.java]
        features.putAll(provider.getElementFeatures(element, sessionStartTime, searchQuery, priority, cache))
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
      val features = hashMapOf<String, Any>()
      features.putAll(context.features)
      features.putAll(getElementFeatures(elementId, element, contributor, priority).features)
      model.predict(features)
    }
  }
}

internal data class SearchEverywhereMLItemInfo(val id: Int, val contributorId: String, val features: Map<String, Any>)