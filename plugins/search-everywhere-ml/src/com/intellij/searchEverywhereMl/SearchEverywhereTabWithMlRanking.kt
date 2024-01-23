package com.intellij.searchEverywhereMl

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrectorContributor

val SE_CONTRIBUTORS = listOf(
  "SearchEverywhereContributor.All", "ClassSearchEverywhereContributor",
  "FileSearchEverywhereContributor", "RecentFilesSEContributor",
  "SymbolSearchEverywhereContributor", "ActionSearchEverywhereContributor",
  "SemanticActionSearchEverywhereContributor",
  "RunConfigurationsSEContributor", "CommandsContributor",
  "TopHitSEContributor", "com.intellij.ide.actions.searcheverywhere.CalculatorSEContributor",
  "TmsSearchEverywhereContributor", "YAMLKeysSearchEverywhereContributor",
  "UrlSearchEverywhereContributor", "Vcs.Git", "AutocompletionContributor",
  "TextSearchContributor", "DbSETablesContributor", "third.party",
  SearchEverywhereSpellingCorrectorContributor::class.java.simpleName,
)

val SE_TABS = listOf(
  ALL_CONTRIBUTORS_GROUP_ID,
  ActionSearchEverywhereContributor::class.java.simpleName,
  FileSearchEverywhereContributor::class.java.simpleName,
  ClassSearchEverywhereContributor::class.java.simpleName,
  SymbolSearchEverywhereContributor::class.java.simpleName,
  "Git"
)

enum class SearchEverywhereTabWithMlRanking(val tabId: String) {
  // Define only tabs for which sorting by ML can be turned on or off in the advanced settings
  ACTION(ActionSearchEverywhereContributor::class.java.simpleName),
  FILES(FileSearchEverywhereContributor::class.java.simpleName),
  CLASSES(ClassSearchEverywhereContributor::class.java.simpleName),
  SYMBOLS(SymbolSearchEverywhereContributor::class.java.simpleName),
  ALL(ALL_CONTRIBUTORS_GROUP_ID);


  companion object {
    fun findById(tabId: String) = values().find { it.tabId == tabId }
  }
}