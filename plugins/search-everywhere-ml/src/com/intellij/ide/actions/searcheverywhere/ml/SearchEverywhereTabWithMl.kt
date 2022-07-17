package com.intellij.ide.actions.searcheverywhere.ml

import com.intellij.ide.actions.searcheverywhere.ActionSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor

internal enum class SearchEverywhereTabWithMl(val tabId: String) {
  // Define only tabs for which sorting by ML can be turned on or off in the advanced settings
  ACTION(ActionSearchEverywhereContributor::class.java.simpleName),
  FILES(FileSearchEverywhereContributor::class.java.simpleName);

  companion object {
    fun findById(tabId: String) = values().find { it.tabId == tabId }
  }
}