package com.intellij.searchEverywhereMl.ranking.core.features

import com.intellij.ide.actions.searcheverywhere.EssentialContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereEssentialContributorMarker
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.local.ContributorsGlobalSummaryManager
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.ContributorsLocalStatisticsFields
import com.intellij.searchEverywhereMl.ranking.core.features.statistician.getContributorStatistics

internal class SearchEverywhereContributorFeaturesProvider {
  companion object {
    private val SE_CONTRIBUTORS = listOf(
      "SearchEverywhereContributor.All", "ClassSearchEverywhereContributor",
      "FileSearchEverywhereContributor", "RecentFilesSEContributor",
      "SymbolSearchEverywhereContributor", "ActionSearchEverywhereContributor",
      "RunConfigurationsSEContributor", "CommandsContributor",
      "TopHitSEContributor", "com.intellij.ide.actions.searcheverywhere.CalculatorSEContributor",
      "TmsSearchEverywhereContributor", "YAMLKeysSearchEverywhereContributor",
      "UrlSearchEverywhereContributor", "Vcs.Git", "AutocompletionContributor",
      "TextSearchContributor", "DbSETablesContributor", "third.party"
    )

    internal val CONTRIBUTOR_INFO_ID = EventFields.String("contributorId", SE_CONTRIBUTORS)
    internal val CONTRIBUTOR_PRIORITY = EventFields.Int("contributorPriority")
    internal val CONTRIBUTOR_WEIGHT = EventFields.Int("contributorWeight")
    internal val CONTRIBUTOR_IS_MOST_POPULAR = EventFields.Boolean("contributorIsMostPopular")
    internal val CONTRIBUTOR_POPULARITY_INDEX = EventFields.Int("contributorPopularityIndex")
    internal val IS_ESSENTIAL_CONTRIBUTOR = EventFields.Boolean("contributorIsEssential")
    internal val ESSENTIAL_CONTRIBUTOR_PREDICTION = EventFields.Float("contributorIsEssentialPrediction")

    private val LOCAL_STATISTICS = ContributorsLocalStatisticsFields()
    private val GLOBAL_STATISTICS = ContributorsGlobalStatisticsFields()

    fun getFeaturesDeclarations(): List<EventField<*>> {
      return listOf(
        CONTRIBUTOR_INFO_ID, CONTRIBUTOR_PRIORITY, CONTRIBUTOR_WEIGHT,
        CONTRIBUTOR_IS_MOST_POPULAR, CONTRIBUTOR_POPULARITY_INDEX,
        IS_ESSENTIAL_CONTRIBUTOR, ESSENTIAL_CONTRIBUTOR_PREDICTION
      ) + LOCAL_STATISTICS.getFieldsDeclaration() + GLOBAL_STATISTICS.getFieldsDeclaration()
    }
  }

  fun getContributorIdFeature(contributor: SearchEverywhereContributor<*>): EventPair<*> {
    return CONTRIBUTOR_INFO_ID.with(contributor.searchProviderId)
  }

  fun getFeatures(contributor: SearchEverywhereContributor<*>, mixedListInfo: SearchEverywhereMixedListInfo,
                  sessionStartTime: Long): List<EventPair<*>> {
    val contributor_id = contributor.searchProviderId
    val info = arrayListOf<EventPair<*>>(
      CONTRIBUTOR_INFO_ID.with(contributor_id),
      CONTRIBUTOR_WEIGHT.with(contributor.sortWeight),
    )

    mixedListInfo.contributorPriorities[contributor.searchProviderId]?.let { priority ->
      info.add(CONTRIBUTOR_PRIORITY.with(priority))
    }

    info.addAll(LOCAL_STATISTICS.getLocalStatistics(contributor_id, sessionStartTime))

    val globalSummary = ContributorsGlobalSummaryManager.getInstance()
    val contributorsStats = globalSummary.getStatistics(contributor_id)
    val maxEventCount = globalSummary.eventCountRange.maxEventCount
    info.addAll(GLOBAL_STATISTICS.getEventGlobalStatistics(contributorsStats, maxEventCount))

    return info + getStatisticianFeatures(contributor)
  }

  fun addEssentialContributorFeature(features: List<EventPair<*>>, contributor: SearchEverywhereContributor<*>): List<EventPair<*>> {
    val marker = SearchEverywhereEssentialContributorMarker.getInstanceOrNull()
    val is_essential_contributor = marker?.isContributorEssential(contributor) ?:
                                   (contributor is EssentialContributor && contributor.isEssential())

    val result = features.toMutableList()
    result.add(IS_ESSENTIAL_CONTRIBUTOR.with(is_essential_contributor))

    marker?.getContributorEssentialPrediction(contributor)?.let { prediction ->
      result.add(ESSENTIAL_CONTRIBUTOR_PREDICTION.with(prediction))
    }

    return result
  }

  private fun getStatisticianFeatures(contributor: SearchEverywhereContributor<*>): List<EventPair<*>> {
    val contributorId = contributor.searchProviderId
    val statistics = getContributorStatistics()

    val isMostPopular = statistics.contributorUsage.firstOrNull()?.first?.equals(contributorId) ?: return emptyList()
    val popularityIndex = statistics.contributorUsage.indexOfFirst { it.first == contributorId }.takeIf { it >= 0 } ?: return emptyList()

    return listOf(
      CONTRIBUTOR_IS_MOST_POPULAR.with(isMostPopular),
      CONTRIBUTOR_POPULARITY_INDEX.with(popularityIndex),
    )
  }
}
