@file:OptIn(IntellijInternalApi::class)

package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereEssentialContributorMarker
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.searchEverywhereMl.SearchEverywhereTab
import com.intellij.searchEverywhereMl.isEssentialContributorPredictionExperiment
import com.intellij.searchEverywhereMl.ranking.core.model.CatBoostModelFactory
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereCatBoostBinaryClassifierModel
import java.util.*


/**
 * Marker that uses machine learning to predict what contributors are essential
 */
internal class SearchEverywhereEssentialContributorMlMarker : SearchEverywhereEssentialContributorMarker {
  companion object {
    private const val MODEL_DIR = "ec_model_exp"
    private const val RESOURCE_DIR = "ec_features_exp"
    private const val TRUE_THRESHOLD = 0.4
  }

  private val model: SearchEverywhereCatBoostBinaryClassifierModel = CatBoostModelFactory()
    .withModelDirectory(MODEL_DIR)
    .withResourceDirectory(RESOURCE_DIR)
    .buildBinaryClassifier(TRUE_THRESHOLD)

  /**
   * A cache that stores predicted probabilities for contributors in various search states.
   * Realistically - we are only interested in the last session, thus we are going to use a weak map,
   * so that past sessions and their related predicted probabilities can be garbage-collected.

   * The key is a `SearchEverywhereMlSearchState` object that represents the state of a search session.
   * The value is a map associating individual contributors (`SearchEverywhereContributor`) with
   * their predicted probabilities (`Float`) for being considered essential in the current search state.
   */
  private val contributorPredictionCache = WeakHashMap<SearchEverywhereMlSearchState, MutableMap<SearchEverywhereContributor<*>, Float>>()

  override fun isAvailable(): Boolean {
    return isActiveExperiment() && isSearchStateActive()
  }

  private fun isActiveExperiment(): Boolean {
    return SearchEverywhereTab.All.isEssentialContributorPredictionExperiment
  }

  private fun isSearchStateActive(): Boolean {
    try {
      val rankingService = checkNotNull(searchEverywhereMlRankingService) { "Search Everywhere Ranking Service is null" }
      val searchSession = checkNotNull(rankingService.getCurrentSession()) { "Search Everywhere Search Session is null" }
      checkNotNull(searchSession.getCurrentSearchState()) { "Search Everywhere Search State is null" }

      // Search state is active
      return true
    } catch (e: IllegalStateException) {
      thisLogger().debug(e)
      return false
    }
  }

  private fun computeProbability(contributor: SearchEverywhereContributor<*>): Float {
    val features = getFeatures(contributor).associate { it.field.name to it.data }
    return model.predict(features).toFloat()
  }

  override fun isContributorEssential(contributor: SearchEverywhereContributor<*>): Boolean {
    val proba = getContributorEssentialPrediction(contributor)
    return proba >= TRUE_THRESHOLD
  }

  internal fun getContributorEssentialPrediction(contributor: SearchEverywhereContributor<*>): Float {
    val searchState = getSearchState()

    val cache = contributorPredictionCache.getOrPut(searchState) { hashMapOf() }
    return cache.getOrPut(contributor) {
      computeProbability(contributor).also { probability ->
        thisLogger().debug("Predicted probability of ${contributor.searchProviderId} is $probability")
      }
    }
  }

  private fun getFeatures(contributor: SearchEverywhereContributor<*>): List<EventPair<*>> {
    val searchSession = getSearchSession()
    val searchState = getSearchState()

    val sessionContextFeatures = searchSession.cachedContextInfo.features
    val stateFeatures = searchState.searchStateFeatures
    val contributorFeatures = searchState.getContributorFeatures(contributor)

    return sessionContextFeatures + stateFeatures + contributorFeatures
  }

  private fun getSearchSession(): SearchEverywhereMLSearchSession {
    val rankingService = checkNotNull(searchEverywhereMlRankingService)
    return checkNotNull(rankingService.getCurrentSession())
  }

  private fun getSearchState(): SearchEverywhereMlSearchState {
    val searchSession = getSearchSession()
    return checkNotNull(searchSession.getCurrentSearchState())
  }
}
