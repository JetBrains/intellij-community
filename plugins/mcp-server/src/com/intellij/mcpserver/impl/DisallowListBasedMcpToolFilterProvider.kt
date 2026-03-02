package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.DisallowMcpTools
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilter
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterModification
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DisallowListBasedMcpToolFilterProvider : McpToolFilterProvider {
  override fun getFilters(clientInfo: Implementation?, sessionOptions: McpServerService.McpSessionOptions?): List<McpToolFilter> {
    val settings = McpToolDisallowListSettings.getInstance()
    return listOf(DisallowListMcpToolFilter(settings.disallowedToolNames))
  }

  override fun getUpdates(clientInfo: Implementation?, scope: CoroutineScope, sessionOptions: McpServerService.McpSessionOptions?): Flow<Unit> {
    val settings = McpToolDisallowListSettings.getInstance()
    return settings.disallowedToolNamesFlow.map { }
  }

  private class DisallowListMcpToolFilter(private val disallowedNames: Set<String>) : McpToolFilter {
    override fun modify(context: McpToolFilterContext): McpToolFilterModification {
      val toolsToDisallow = context.allowedTools.filter { it.descriptor.name in disallowedNames }.toSet()
      return DisallowMcpTools(toolsToDisallow)
    }
  }
}
