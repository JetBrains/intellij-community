package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DisallowListBasedMcpToolFilterProvider : McpToolFilterProvider {
  override fun applyFilters(context: McpToolFilterContext, clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions?, invocationMode: McpToolInvocationMode) {
    val settings = McpToolDisallowListSettings.getInstance()
    val toolStates = settings.toolStates
    context.turnOn { tool ->
      toolStates[tool.descriptor.name]?.let { toolState ->
        toolState.enabled && !toolState.onDemand
      } == true
    }
    context.turnOff { tool ->
      toolStates[tool.descriptor.name]?.enabled == false
    }
  }

  override fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions?, invocationMode: McpToolInvocationMode): Flow<Unit> {
    val settings = McpToolDisallowListSettings.getInstance()
    return settings.toolStatesFlow.map { }
  }
}
