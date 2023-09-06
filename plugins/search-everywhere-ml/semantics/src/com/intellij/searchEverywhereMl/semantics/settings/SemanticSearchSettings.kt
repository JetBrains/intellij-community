package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.openapi.application.ApplicationManager

interface SemanticSearchSettings {
  var enabledInActionsTab: Boolean
  var enabledInFilesTab: Boolean
  var enabledInSymbolsTab: Boolean
  var enabledInClassesTab: Boolean

  fun isEnabled(): Boolean
  fun getUseRemoteActionsServer(): Boolean
  fun getActionsAPIToken(): String

  companion object {
    fun getInstance(): SemanticSearchSettings = ApplicationManager.getApplication().getService(SemanticSearchSettings::class.java)
  }
}