package com.intellij.searchEverywhereMl.semantics.settings

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.APP)
class MockSearchEverywhereSemanticSettings: SearchEverywhereSemanticSettings {
  override var enabledInActionsTab = false
  override var enabledInFilesTab = false
  override var enabledInSymbolsTab = false
  override var enabledInClassesTab = false

  override fun isEnabled() = true

  override fun getUseRemoteActionsServer() = false
  override fun getActionsAPIToken() = ""
  override fun getEnabledInClassesTabState(): StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
  override fun getEnabledInSymbolsTabState(): StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
}