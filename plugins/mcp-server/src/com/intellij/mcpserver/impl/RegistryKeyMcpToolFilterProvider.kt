package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolFilterProvider.MaskBasedMcpToolFilter.Companion.getMaskFilters
import com.intellij.openapi.util.registry.Registry
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class RegistryKeyMcpToolFilterProvider(val cs: CoroutineScope) : McpToolFilterProvider {
  override fun getFilters(clientInfo: Implementation?): StateFlow<List<McpToolFilterProvider.McpToolFilter>> {
    return Registry.get("mcp.server.tools.filter").asStringFlow()
      .map { getMaskFilters(it) }
      .stateIn(cs, SharingStarted.Eagerly, getMaskFilters(Registry.stringValue("mcp.server.tools.filter")))
  }
}
