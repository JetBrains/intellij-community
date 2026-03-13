package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.MaskBasedMcpToolFilter.Companion.getMaskFilters
import com.intellij.mcpserver.settings.McpToolFilterSettings
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class SettingsBasedMcpToolFilterProvider : McpToolFilterProvider {
  override fun getFilters(clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions?): List<McpToolFilterProvider.McpToolFilter> {
    val settings = McpToolFilterSettings.getInstance()
    return getMaskFilters(settings.toolsFilter)
  }

  override fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions?): Flow<Unit> {
    val settings = McpToolFilterSettings.getInstance()
    return settings.toolsFilterFlow.map { }
  }
}
