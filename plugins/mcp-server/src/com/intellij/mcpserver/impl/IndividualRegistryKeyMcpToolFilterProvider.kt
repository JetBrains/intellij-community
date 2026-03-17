package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.openapi.util.registry.Registry
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/**
 * Mapping of fully qualified tool names to their corresponding registry keys.
 *
 * When the registry key value is `false`, the tool is disabled.
 * When the registry key value is `true`, the tool is enabled.
 */
private val TOOL_REGISTRY_KEYS: Map<String, String> = mapOf()

/**
 * Filters individual MCP tools based on specific boolean registry keys.
 *
 * Each tool can be enabled/disabled via its own registry key. When the registry key is `false`,
 * the corresponding tool is disallowed.
 *
 * To add a new tool to this filter:
 * 1. Add the registry key in plugin.xml (e.g., `mcp.server.tools.enable.my.tool`)
 * 2. Add a new entry to [TOOL_REGISTRY_KEYS] mapping the fully qualified tool name to its registry key
 */
internal class IndividualRegistryKeyMcpToolFilterProvider : McpToolFilterProvider {

  override fun applyFilters(context: McpToolFilterContext, clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions?) {
    val disabledToolNames = TOOL_REGISTRY_KEYS
      .filter { (_, registryKey) -> !Registry.`is`(registryKey) }
      .map { (toolFqn, _) -> toolFqn }
      .toSet()
    if (disabledToolNames.isNotEmpty()) {
      context.turnOff { it.descriptor.name in disabledToolNames }
    }
  }

  override fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions?): Flow<Unit> {
    val flows = TOOL_REGISTRY_KEYS.values.map { registryKey ->
      Registry.get(registryKey).asBooleanFlow()
    }
    return flows.merge().let { mergedFlow ->
      flow {
        mergedFlow.collect { emit(Unit) }
      }
    }
  }
}
