package com.intellij.searchEverywhereMl

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorContributor

val SE_TABS = listOf(
  "SearchEverywhereContributor.All", "ClassSearchEverywhereContributor",
  "FileSearchEverywhereContributor", "RecentFilesSEContributor",
  "SymbolSearchEverywhereContributor", "ActionSearchEverywhereContributor",
  "RunConfigurationsSEContributor", "CommandsContributor",
  "TopHitSEContributor", "com.intellij.ide.actions.searcheverywhere.CalculatorSEContributor",
  "TmsSearchEverywhereContributor", "YAMLKeysSearchEverywhereContributor",
  "UrlSearchEverywhereContributor", "Vcs.Git", "AutocompletionContributor",
  "TextSearchContributor", "DbSETablesContributor", "third.party",
  SearchEverywhereSpellingCorrectorContributor::class.java.simpleName,
)

enum class SearchEverywhereTabWithMlRanking(val tabId: String) {
  // Define only tabs for which sorting by ML can be turned on or off in the advanced settings
  ACTION("Actions"),
  FILES("Files"),
  CLASSES("Classes"),
  ALL(ALL_CONTRIBUTORS_GROUP_ID);


  companion object {
    fun findById(tabId: String) = values().find { it.tabId == tabId }
  }
}