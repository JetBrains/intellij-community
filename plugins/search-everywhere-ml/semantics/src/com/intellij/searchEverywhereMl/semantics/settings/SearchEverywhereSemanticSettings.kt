package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.openapi.components.service

interface SearchEverywhereSemanticSettings {
  var enabledInActionsTab: Boolean
  var enabledInFilesTab: Boolean
  var enabledInClassesTab: Boolean
  var enabledInSymbolsTab: Boolean

  fun isEnabled(): Boolean

  fun getUseRemoteActionsServer(): Boolean
  fun getActionsAPIToken(): String

  fun isEnabledInTab(providerId: String): Boolean = when (providerId) {
    ActionSearchEverywhereContributor::class.java.simpleName -> enabledInActionsTab
    FileSearchEverywhereContributor::class.java.simpleName -> enabledInFilesTab
    ClassSearchEverywhereContributor::class.java.simpleName -> enabledInClassesTab
    SymbolSearchEverywhereContributor::class.java.simpleName -> enabledInSymbolsTab
    else -> false
  }

  companion object {
    fun getInstance(): SearchEverywhereSemanticSettings = service<SearchEverywhereSemanticSettings>()
  }
}