package com.intellij.mcpserver

import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

fun applyMaskFilter(context: McpToolFilterContext, maskList: String) {
  if (maskList.isBlank()) return
  val masks = MaskList(maskList)
  context.updateState(enabled = false) { tool -> !masks.matches(tool.descriptor.fullyQualifiedName) }
}

interface McpToolFilterProvider {
  data class McpToolState(
    val enabled: Boolean = true,
    val routerOnly: Boolean = true
  )

  class McpToolFilterContext(tools: Collection<McpTool>) {
    private val toolStates: MutableMap<McpTool, McpToolState> = tools.associateWith { McpToolState() }.toMutableMap()

    val onTools: Set<McpTool> get() = toolStates.filterValues { it.enabled && !it.routerOnly }.keys
    val routerOnlyTools: Set<McpTool> get() = toolStates.filterValues { it.enabled && it.routerOnly }.keys

    fun updateState(state: McpToolState, predicate: (McpTool) -> Boolean) {
      updateState(state.enabled, state.routerOnly, predicate)
    }

    fun updateState(enabled: Boolean? = null, routerOnly: Boolean? = null, predicate: (McpTool) -> Boolean) {
      if (enabled == null && routerOnly == null) {
        thisLogger().warn("MCP tool update states were not defined, skipping state update")
        return
      }
      for ((tool, state) in toolStates) {
        if (predicate(tool)) {
          if ((enabled == null || state.enabled == enabled) && (routerOnly == null || state.routerOnly == routerOnly)) continue
          toolStates[tool] = state.copy(enabled = enabled ?: state.enabled, routerOnly = routerOnly ?: state.routerOnly)
        }
      }
    }
  }

  companion object {
    val EP: ExtensionPointName<McpToolFilterProvider> = ExtensionPointName.create("com.intellij.mcpServer.mcpToolFilterProvider")
  }

  fun applyFilters(context: McpToolFilterContext, clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions? = null, invocationMode: McpToolInvocationMode = McpToolInvocationMode.DIRECT)

  fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions? = null, invocationMode: McpToolInvocationMode = McpToolInvocationMode.DIRECT): Flow<Unit>
}
