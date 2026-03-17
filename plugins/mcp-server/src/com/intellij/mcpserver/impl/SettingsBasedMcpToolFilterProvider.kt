package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.settings.McpToolFilterSettings
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class SettingsBasedMcpToolFilterProvider : McpToolFilterProvider {
  override fun applyFilters(context: McpToolFilterContext, clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions?) {
    val settings = McpToolFilterSettings.getInstance()
    McpToolFilterProvider.applyMaskFilter(context, settings.toolsFilter)
  }

  override fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions?): Flow<Unit> {
    val settings = McpToolFilterSettings.getInstance()
    return settings.toolsFilterFlow.map { }
  }
}
