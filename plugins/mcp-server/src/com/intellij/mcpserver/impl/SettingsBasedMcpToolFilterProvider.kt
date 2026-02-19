package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.MaskBasedMcpToolFilter.Companion.getMaskFilters
import com.intellij.mcpserver.settings.McpToolFilterSettings
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class SettingsBasedMcpToolFilterProvider(val cs: CoroutineScope) : McpToolFilterProvider {
  override fun getFilters(clientInfo: Implementation?): StateFlow<List<McpToolFilterProvider.McpToolFilter>> {
    val settings = McpToolFilterSettings.getInstance()
    return settings.toolsFilterFlow
      .map { getMaskFilters(it) }
      .stateIn(cs, SharingStarted.Lazily, getMaskFilters(settings.toolsFilter))
  }
}
