package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class MockSearchEverywhereSemanticSettings: SearchEverywhereSemanticSettings {
  override var enabledInActionsTab = false
  override var enabledInFilesTab = false
  override var enabledInSymbolsTab = false
  override var enabledInClassesTab = false

  override fun isEnabled() = true

  override fun getUseRemoteActionsServer() = false
  override fun getActionsAPIToken() = ""
}