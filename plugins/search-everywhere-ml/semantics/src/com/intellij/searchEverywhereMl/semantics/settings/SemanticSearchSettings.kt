package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.openapi.application.ApplicationManager

interface SemanticSearchSettings {
  var enabledInActionsTab: Boolean
  var enabledInFilesTab: Boolean
  var enabledInSymbolsTab: Boolean
  var enabledInClassesTab: Boolean

  val manuallyDisabledInActionsTab: Boolean
    get() = false
  val manuallyDisabledInFilesTab: Boolean
    get() = false
  val manuallyDisabledInSymbolsTab: Boolean
    get() = false
  val manuallyDisabledInClassesTab: Boolean
    get() = false

  fun isEnabled(): Boolean
  fun getUseRemoteActionsServer(): Boolean
  fun getActionsAPIToken(): String

  companion object {
    fun getInstance(): SemanticSearchSettings = ApplicationManager.getApplication().getService(SemanticSearchSettings::class.java)
  }
}