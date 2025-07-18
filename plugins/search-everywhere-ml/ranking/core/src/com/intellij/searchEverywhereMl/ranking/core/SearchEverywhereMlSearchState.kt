// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereState
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.features.SearchEverywhereStateFeaturesProvider
import com.intellij.searchEverywhereMl.isTabWithMlRanking
import com.intellij.searchEverywhereMl.ranking.core.features.FeaturesProviderCache
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereContributorFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider
import com.intellij.searchEverywhereMl.ranking.core.features.SearchEverywhereElementFeaturesProvider.Companion.ML_SCORE_KEY
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereModelProvider
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereRankingModel
import com.intellij.util.applyIf

internal class SearchEverywhereMlSearchState(
  override val project: Project?,
  override val index: Int,
  override val tab: SearchEverywhereTab,
  override val searchScope: ScopeDescriptor?,
  override val isSearchEverywhere: Boolean,
  override val sessionStartTime: Long,
  override val searchRestartReason: SearchRestartReason,
  override val keysTyped: Int,
  override val backspacesTyped: Int,
  override val query: String,
  private val modelProvider: SearchEverywhereModelProvider,
  private val providersCache: FeaturesProviderCache?,
  private val mixedListInfo: SearchEverywhereMixedListInfo,
) : SearchEverywhereState {
  override val stateStartTime: Long = System.currentTimeMillis()

  override val experimentGroup: Int = SearchEverywhereMlExperiment.experimentGroup

  val searchStateFeatures = SearchEverywhereStateFeaturesProvider.getFeatures(this)

  val orderByMl: Boolean
    get() {
      if (!tab.isTabWithMlRanking()) {
        return false
      }

      if (tab == SearchEverywhereTab.All && query.isEmpty()) {
        return false
      }

      return tab.isMlRankingEnabled
    }

  private val model: SearchEverywhereRankingModel by lazy { modelProvider.getModel(tab as SearchEverywhereTab.TabWithMlRanking) }

  fun getElementFeatures(element: Any,
                         contributor: SearchEverywhereContributor<*>,
                         contributorFeatures: List<EventPair<*>>,
                         priority: Int,
                         context: SearchEverywhereMLContextInfo,
                         correction: SearchEverywhereSpellCheckResult): List<EventPair<*>> {
    return SearchEverywhereElementFeaturesProvider.getFeatureProvidersForContributor(contributor.searchProviderId)
      .flatMap { featuresProvider ->
        featuresProvider.getElementFeatures(element, sessionStartTime, query, priority, providersCache, correction)
      }
      .applyIf(tab == SearchEverywhereTab.All) {
        val mlScore = getElementMLScoreForAllTab(contributor.searchProviderId, context.features, this, contributorFeatures)
        if (mlScore == null) {
          return@applyIf this
        } else {
          return@applyIf this + listOf(ML_SCORE_KEY.with(mlScore))
        }
      }
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
                  elementFeatures: List<EventPair<*>>,
                  contributorFeatures: List<EventPair<*>>): Double {
    val features = getAllFeatures(context.features, elementFeatures, contributorFeatures)
    return model.predict(features)
  }

  fun getContributorFeatures(contributor: SearchEverywhereContributor<*>): List<EventPair<*>> {
    return SearchEverywhereContributorFeaturesProvider.getFeatures(contributor, mixedListInfo, sessionStartTime)
  }
}
