package com.intellij.searchEverywhereMl.ranking.core

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMixedListInfo
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultList

internal data class SearchResultProvidersInfo(
  val isMixedList: Boolean,
  val providerPriorities: Map<String, Int>,
) {
  companion object {
    val EMPTY = SearchResultProvidersInfo(false, emptyMap())

    fun fromMixedListInfo(mixedListInfo: SearchEverywhereMixedListInfo?): SearchResultProvidersInfo {
      if (mixedListInfo == null) return EMPTY
      return SearchResultProvidersInfo(mixedListInfo.isMixedList, mixedListInfo.contributorPriorities)
    }

    fun forSplitSession(): SearchResultProvidersInfo {
      val contributorPriorities = SeResultList.prioritizedProvidersPriorities.mapKeys { (providerId, _) -> providerId.value }
      return SearchResultProvidersInfo(true, contributorPriorities)
    }
  }
}
