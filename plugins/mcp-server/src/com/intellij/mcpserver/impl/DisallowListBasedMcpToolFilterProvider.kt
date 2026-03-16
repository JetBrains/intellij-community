package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.DisallowMcpTools
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilter
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterModification
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class DisallowListBasedMcpToolFilterProvider(val cs: CoroutineScope) : McpToolFilterProvider {
  override fun getFilters(clientInfo: Implementation?): StateFlow<List<McpToolFilter>> {
    val settings = McpToolDisallowListSettings.getInstance()
    return settings.disallowedToolNamesFlow
      .map { disallowedNames -> listOf(DisallowListMcpToolFilter(disallowedNames)) }
      .stateIn(cs, SharingStarted.Lazily, listOf(DisallowListMcpToolFilter(settings.disallowedToolNames)))
  }

  private class DisallowListMcpToolFilter(private val disallowedNames: Set<String>) : McpToolFilter {
    override fun modify(context: McpToolFilterContext): McpToolFilterModification {
      val toolsToDisallow = context.allowedTools.filter { it.descriptor.name in disallowedNames }.toSet()
      return DisallowMcpTools(toolsToDisallow)
    }
  }
}
