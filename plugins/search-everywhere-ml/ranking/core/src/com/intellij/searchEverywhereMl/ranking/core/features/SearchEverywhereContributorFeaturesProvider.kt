package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.searcheverywhere.EssentialContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereEssentialContributorMarker
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.ALLOWED_CONTRIBUTOR_ID_LIST
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.ContributorsGlobalSummaryManager
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereEssentialContributorMlMarker
import com.intellij.searchEverywhereMl.ranking.core.SearchEverywhereMLSearchSession
import com.intellij.searchEverywhereMl.ranking.core.adapters.LegacyContributorAdapter
import com.intellij.searchEverywhereMl.ranking.core.adapters.SearchResultProviderAdapter
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.ContributorsLocalStatisticsFields
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.getProviderStatistics

internal object SearchEverywhereContributorFeaturesProvider {
  val CONTRIBUTOR_INFO_ID = EventFields.String("contributor_id", ALLOWED_CONTRIBUTOR_ID_LIST)
  val CONTRIBUTOR_PRIORITY = EventFields.Int("contributor_priority")
  val CONTRIBUTOR_WEIGHT = EventFields.Int("contributor_weight")
  val CONTRIBUTOR_IS_MOST_POPULAR = EventFields.Boolean("contributor_is_most_popular")
  val CONTRIBUTOR_POPULARITY_INDEX = EventFields.Int("contributor_popularity_index")
  val IS_ESSENTIAL_CONTRIBUTOR = EventFields.Boolean("contributor_is_essential")
  val ESSENTIAL_CONTRIBUTOR_PREDICTION = EventFields.Float("contributor_is_essential_prediction")

  private val LOCAL_STATISTICS = ContributorsLocalStatisticsFields()
  private val GLOBAL_STATISTICS = ContributorsGlobalStatisticsFields()

  fun getFeaturesDeclarations(): List<EventField<*>> {
    return listOf(
      CONTRIBUTOR_INFO_ID,
      CONTRIBUTOR_PRIORITY, CONTRIBUTOR_WEIGHT,
      CONTRIBUTOR_IS_MOST_POPULAR, CONTRIBUTOR_POPULARITY_INDEX,
      IS_ESSENTIAL_CONTRIBUTOR, ESSENTIAL_CONTRIBUTOR_PREDICTION
    ) + LOCAL_STATISTICS.getFieldsDeclaration() + GLOBAL_STATISTICS.getFieldsDeclaration()
  }

  /**
   * Collects features for a contributor.
   * 
   * Essential Contributor (EC) features are intentionally not included here to avoid a circular dependency.
   * Instead, EC features are collected separately in getEssentialContributorFeatures().
   */
  fun getFeatures(provider: SearchResultProviderAdapter,
                  sessionStartTime: Long,
                  providerPriority: Int? = null,
                  providerWeight: Int? = null): List<EventPair<*>> {
    val contributor_id = provider.id
    val info = arrayListOf<EventPair<*>>(
      CONTRIBUTOR_INFO_ID.with(contributor_id),
    )

    providerPriority?.let { info.add(CONTRIBUTOR_PRIORITY.with(it)) }
    providerWeight?.let { info.add(CONTRIBUTOR_WEIGHT.with(it)) }

    info.addAll(LOCAL_STATISTICS.getLocalStatistics(contributor_id, sessionStartTime))

    val globalSummary = ContributorsGlobalSummaryManager.getInstance()
    val contributorsStats = globalSummary.getStatistics(contributor_id)
    val maxEventCount = globalSummary.eventCountRange.maxEventCount
    info.addAll(GLOBAL_STATISTICS.getEventGlobalStatistics(contributorsStats, maxEventCount))

    return info + getStatisticianFeatures(provider)
  }

  /**
   * Collects Essential Contributor (EC) features for a contributor.
   *
   * EC features are the predictions of the EC model, which itself needs contributor features to make predictions.
   */
  fun getEssentialContributorFeatures(searchState: SearchEverywhereMLSearchSession.SearchState,
                                      provider: SearchResultProviderAdapter): List<EventPair<*>> {
    val marker = SearchEverywhereEssentialContributorMarker.getInstanceOrNull()
    if (marker == null) {
      // In the case where we do not have a marker available, we will log the default essential behavior,
      // so we can simply rely on EssentialContributor.checkEssential
      if (provider is LegacyContributorAdapter) {
        return listOf(
          IS_ESSENTIAL_CONTRIBUTOR.with(EssentialContributor.checkEssential(provider.contributor))
        )
      } else {
        return emptyList()
      }
    }

    // Here - we do not want to call EssentialContributor.checkEssential, because that would get
    // the current state, which, at the point where we are calculating features to report,
    // is the one ahead (we are calculating features for the previous state)

    marker as SearchEverywhereEssentialContributorMlMarker
    val cachedPrediction = marker.getContributorEssentialPrediction(provider, searchState)

    return listOf(
      IS_ESSENTIAL_CONTRIBUTOR.with(cachedPrediction >= SearchEverywhereEssentialContributorMlMarker.TRUE_THRESHOLD),
      ESSENTIAL_CONTRIBUTOR_PREDICTION.with(cachedPrediction)
    )
  }

  private fun getStatisticianFeatures(provider: SearchResultProviderAdapter): List<EventPair<*>> {
    val statistics = getProviderStatistics()

    val isMostPopular = statistics.contributorUsage.firstOrNull()?.first?.equals(provider.id) ?: return emptyList()
    val popularityIndex = statistics.contributorUsage.indexOfFirst { it.first == provider.id }.takeIf { it >= 0 } ?: return emptyList()

    return listOf(
      CONTRIBUTOR_IS_MOST_POPULAR.with(isMostPopular),
      CONTRIBUTOR_POPULARITY_INDEX.with(popularityIndex),
    )
  }
}
