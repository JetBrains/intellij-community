package com.intellij.searchEverywhereMl.semantics.tests

import com.intellij.openapi.components.Service
import com.intellij.searchEverywhereMl.semantics.settings.SemanticSearchSettings

@Service(Service.Level.APP)
class MockSemanticSearchSettings : SemanticSearchSettings {
  override var enabledInActionsTab = false
  override var enabledInFilesTab = false
  override var enabledInSymbolsTab = false
  override var enabledInClassesTab = false

  override fun isEnabled() = true
  override fun getUseRemoteActionsServer() = false
  override fun getActionsAPIToken() = ""
}