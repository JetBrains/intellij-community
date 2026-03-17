package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilter
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterModification
import com.intellij.mcpserver.McpToolFilterProvider.McpToolState
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DisallowListBasedMcpToolFilterProvider : McpToolFilterProvider {
  override fun getFilters(clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions?): List<McpToolFilter> {
    val settings = McpToolDisallowListSettings.getInstance()
    return listOf(ToolStateBasedMcpToolFilter(settings.toolStates))
  }

  override fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions?): Flow<Unit> {
    val settings = McpToolDisallowListSettings.getInstance()
    return settings.toolStatesFlow.map { }
  }

  private class ToolStateBasedMcpToolFilter(private val toolStates: Map<String, McpToolState>) : McpToolFilter {
    override fun modify(context: McpToolFilterContext): McpToolFilterModification {
      return object : McpToolFilterModification {
        override fun apply(context: McpToolFilterContext): McpToolFilterContext {
          val newStates = context.toolStates.toMutableMap()
          for ((tool, currentState) in context.toolStates) {
            val desiredState = toolStates[tool.descriptor.name]
            if (desiredState != null && currentState == McpToolState.ON_DEMAND) {
              newStates[tool] = desiredState
            }
          }
          return context.copy(toolStates = newStates)
        }
      }
    }
  }
}
