package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.ide.actions.searcheverywhere.ml.features.statistician.SearchEverywhereContributorStatistician
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair

internal class SearchEverywhereContributorFeaturesProvider {
  companion object {
    internal val SE_TABS = listOf(
      "SearchEverywhereContributor.All", "ClassSearchEverywhereContributor",
      "FileSearchEverywhereContributor", "RecentFilesSEContributor",
      "SymbolSearchEverywhereContributor", "ActionSearchEverywhereContributor",
      "RunConfigurationsSEContributor", "CommandsContributor",
      "TopHitSEContributor", "com.intellij.ide.actions.searcheverywhere.CalculatorSEContributor",
      "TmsSearchEverywhereContributor", "YAMLKeysSearchEverywhereContributor",
      "UrlSearchEverywhereContributor", "Vcs.Git", "AutocompletionContributor",
      "TextSearchContributor", "DbSETablesContributor", "third.party"
    )

    internal val CONTRIBUTOR_INFO_ID = EventFields.String("contributorId", SE_TABS)
    internal val CONTRIBUTOR_PRIORITY = EventFields.Int("contributorPriority")
    internal val CONTRIBUTOR_WEIGHT = EventFields.Int("contributorWeight")
    internal val CONTRIBUTOR_IS_MOST_POPULAR = EventFields.Boolean("contributorIsMostPopular")
    internal val CONTRIBUTOR_POPULARITY_INDEX = EventFields.Int("contributorPopularityIndex")

    fun getFeaturesDeclarations(): List<EventField<*>> = listOf(
      CONTRIBUTOR_INFO_ID, CONTRIBUTOR_PRIORITY, CONTRIBUTOR_WEIGHT,
      CONTRIBUTOR_IS_MOST_POPULAR, CONTRIBUTOR_POPULARITY_INDEX
    )
  }

  fun getContributorIdFeature(contributor: SearchEverywhereContributor<*>): EventPair<*> {
    return CONTRIBUTOR_INFO_ID.with(contributor.searchProviderId)
  }

  fun getFeatures(contributor: SearchEverywhereContributor<*>, mixedListInfo: SearchEverywhereMixedListInfo): List<EventPair<*>> {
    val info = arrayListOf<EventPair<*>>(
      CONTRIBUTOR_INFO_ID.with(contributor.searchProviderId),
      CONTRIBUTOR_WEIGHT.with(contributor.sortWeight),
    )

    mixedListInfo.contributorPriorities[contributor.searchProviderId]?.let { priority ->
      info.add(CONTRIBUTOR_PRIORITY.with(priority))
    }

    return info + getStatisticianFeatures(contributor)
  }

  private fun getStatisticianFeatures(contributor: SearchEverywhereContributor<*>): List<EventPair<*>> {
    val contributorId = contributor.searchProviderId
    val statistics = SearchEverywhereContributorStatistician.getStatistics()

    val isMostPopular = statistics.contributorUsage.firstOrNull()?.first?.equals(contributorId) ?: return emptyList()
    val popularityIndex = statistics.contributorUsage.indexOfFirst { it.first == contributorId }.takeIf { it >= 0 } ?: return emptyList()

    return listOf(
      CONTRIBUTOR_IS_MOST_POPULAR.with(isMostPopular),
      CONTRIBUTOR_POPULARITY_INDEX.with(popularityIndex),
    )
  }
}