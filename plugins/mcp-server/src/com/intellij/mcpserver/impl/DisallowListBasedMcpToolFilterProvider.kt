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

    context.updateState(enabled = true) { tool ->
      toolStates[tool.descriptor.name]?.enabled == true
    }

    context.updateState(enabled = false) { tool ->
      toolStates[tool.descriptor.name]?.enabled == false
    }

    context.updateState(routerOnly = true) { tool ->
      toolStates[tool.descriptor.name]?.routerOnly == true
    }

    context.updateState(routerOnly = false) { tool ->
      toolStates[tool.descriptor.name]?.routerOnly == false
    }
  }

  override fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions?, invocationMode: McpToolInvocationMode): Flow<Unit> {
    val settings = McpToolDisallowListSettings.getInstance()
    return settings.toolStatesFlow.map { }
  }
}
