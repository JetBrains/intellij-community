package com.intellij.searchEverywhereMl.common

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID

val SE_TABS = listOf(
  "SearchEverywhereContributor.All", "ClassSearchEverywhereContributor",
  "FileSearchEverywhereContributor", "RecentFilesSEContributor",
  "SymbolSearchEverywhereContributor", "ActionSearchEverywhereContributor",
  "RunConfigurationsSEContributor", "CommandsContributor",
  "TopHitSEContributor", "com.intellij.ide.actions.searcheverywhere.CalculatorSEContributor",
  "TmsSearchEverywhereContributor", "YAMLKeysSearchEverywhereContributor",
  "UrlSearchEverywhereContributor", "Vcs.Git", "AutocompletionContributor",
  "TextSearchContributor", "DbSETablesContributor", "third.party"
)

enum class SearchEverywhereTabWithMlRanking(val tabId: String) {
  // Define only tabs for which sorting by ML can be turned on or off in the advanced settings
  ACTION(ActionSearchEverywhereContributor::class.java.simpleName),
  FILES(FileSearchEverywhereContributor::class.java.simpleName),
  CLASSES(ClassSearchEverywhereContributor::class.java.simpleName),
  ALL(ALL_CONTRIBUTORS_GROUP_ID);


  companion object {
    fun findById(tabId: String) = values().find { it.tabId == tabId }
  }
}