package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereEssentialContributorMarker
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.searchEverywhereMl.SearchEverywhereMlExperiment
import com.intellij.searchEverywhereMl.SearchEverywhereTabWithMlRanking
import com.intellij.searchEverywhereMl.ranking.core.model.CatBoostModelFactory
import com.intellij.searchEverywhereMl.ranking.core.model.SearchEverywhereCatBoostBinaryClassifierModel


/**
 * Marker that uses machine learning to predict what contributors are essential
 */
internal class SearchEverywhereEssentialContributorMlMarker : SearchEverywhereEssentialContributorMarker {
  companion object {
    private const val MODEL_DIR = "ec_model_exp"
    private const val RESOURCE_DIR = "ec_features_exp"
    private const val TRUE_THRESHOLD = 0.5
  }

  private val model: SearchEverywhereCatBoostBinaryClassifierModel = CatBoostModelFactory()
    .withModelDirectory(MODEL_DIR)
    .withResourceDirectory(RESOURCE_DIR)
    .buildBinaryClassifier(TRUE_THRESHOLD)

  private val isExperimentDisabledByRegistry
    get() = Registry.`is`("search.everywhere.force.disable.experiment.essential.contributors.ml")

  override fun isAvailable(): Boolean {
    return false  // TODO: Remove this line and edit model and resource directories defined above once a model is ready
    // return isActiveExperiment() && isSearchStateActive()
  }

  private fun isActiveExperiment(): Boolean {
    if (isExperimentDisabledByRegistry) {
      return false
    }

    val experiment = SearchEverywhereMlExperiment()
    val experimentForAllTab = experiment.getExperimentForTab(SearchEverywhereTabWithMlRanking.ALL)
    return experimentForAllTab == SearchEverywhereMlExperiment.ExperimentType.ESSENTIAL_CONTRIBUTOR_PREDICTION
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

  override fun isContributorEssential(contributor: SearchEverywhereContributor<*>): Boolean? {
    val features = getFeatures(contributor).associate { it.field.name to it.data }
    return model.predictTrue(features)
  }

  private fun getFeatures(contributor: SearchEverywhereContributor<*>): List<EventPair<*>> {
    val rankingService = checkNotNull(searchEverywhereMlRankingService)
    val searchSession = checkNotNull(rankingService.getCurrentSession())
    val searchState = checkNotNull(searchSession.getCurrentSearchState())

    val sessionContextFeatures = searchSession.cachedContextInfo.features
    val stateFeatures = searchState.searchStateFeatures
    val contributorFeatures = searchState.getContributorFeatures(contributor)

    return sessionContextFeatures + stateFeatures + contributorFeatures
  }
}