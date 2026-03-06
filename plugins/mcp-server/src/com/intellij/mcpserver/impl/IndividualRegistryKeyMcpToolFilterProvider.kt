package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.DisallowMcpTools
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilter
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterModification
import com.intellij.openapi.util.registry.Registry
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

internal const val ENABLE_GIT_STATUS_TOOL_REGISTRY_KEY: String = "mcp.server.tools.enable.git.status"
internal const val ENABLE_APPLY_PATCH_TOOL_REGISTRY_KEY: String = "mcp.server.tools.enable.apply.patch"
private const val GIT_STATUS_TOOL_NAME: String = "git_status"
private const val APPLY_PATCH_TOOL_NAME: String = "apply_patch"

/**
 * Mapping of fully qualified tool names to their corresponding registry keys.
 *
 * When the registry key value is `false`, the tool is disabled.
 * When the registry key value is `true`, the tool is enabled.
 */
private val TOOL_REGISTRY_KEYS: Map<String, String> = mapOf(
  GIT_STATUS_TOOL_NAME to ENABLE_GIT_STATUS_TOOL_REGISTRY_KEY,
  APPLY_PATCH_TOOL_NAME to ENABLE_APPLY_PATCH_TOOL_REGISTRY_KEY,
)

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

  override fun getFilters(clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions?): List<McpToolFilter> {
    val disabledToolNames = TOOL_REGISTRY_KEYS
      .filter { (_, registryKey) -> !Registry.`is`(registryKey) }
      .map { (toolFqn, _) -> toolFqn }
      .toSet()
    return if (disabledToolNames.isNotEmpty()) {
      listOf(IndividualToolFilter(disabledToolNames))
    }
    else {
      emptyList()
    }
  }

  override fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions?): Flow<Unit> {
    val flows = TOOL_REGISTRY_KEYS.values.map { registryKey ->
      Registry.get(registryKey).asBooleanFlow()
    }
    return flows.merge().let { flow ->
      kotlinx.coroutines.flow.flow {
        flow.collect { emit(Unit) }
      }
    }
  }

  private class IndividualToolFilter(private val disabledToolNames: Set<String>) : McpToolFilter {
    override fun modify(context: McpToolFilterContext): McpToolFilterModification {
      val toolsToDisallow = context.allowedTools.filter { it.descriptor.name in disabledToolNames }.toSet()
      return DisallowMcpTools(toolsToDisallow)
    }
  }
}
