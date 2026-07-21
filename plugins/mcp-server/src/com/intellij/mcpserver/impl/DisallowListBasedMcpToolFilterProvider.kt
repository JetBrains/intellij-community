package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DisallowListBasedMcpToolFilterProvider : UserConfigurableMcpToolFilterProvider {
  override fun applyFilters(
    context: McpToolFilterContext,
    clientInfo: Implementation?,
    sessionOptions: McpServerService.McpSessionOptions?,
    invocationMode: McpToolInvocationMode,
  ) {
    val settings = McpToolDisallowListSettings.getInstance()

    context.updateState(enabled = true) {
      it.isUserConfigurable && settings.toolStateFor(it).enabled
    }

    context.updateState(enabled = false) {
      it.isUserConfigurable && !settings.toolStateFor(it).enabled
    }

    context.updateState(routerOnly = true) {
      it.isUserConfigurable && settings.toolStateFor(it).routerOnly
    }

    context.updateState(routerOnly = false) {
      it.isUserConfigurable && !settings.toolStateFor(it).routerOnly
    }
  }

  override fun getUpdates(
    clientInfo: Implementation?,
    scope: CoroutineScope,
    sessionOptions: McpServerService.McpSessionOptions?,
    invocationMode: McpToolInvocationMode,
  ): Flow<Unit> {
    val settings = McpToolDisallowListSettings.getInstance()
    return settings.toolStatesFlow.map { }
  }
}
