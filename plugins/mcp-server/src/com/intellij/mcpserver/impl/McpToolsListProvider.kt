package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.McpToolset
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val logger = logger<McpToolsListProvider>()

/**
 * Provides a [StateFlow] of all available MCP tools.
 * Subscribes to [McpToolsProvider] and [McpToolset] extension point changes and updates the flow accordingly.
 */
internal class McpToolsListProvider(scope: CoroutineScope) {
  private val _allTools = MutableStateFlow(getAllMcpTools())
  
  /**
   * StateFlow containing all currently available MCP tools from all providers.
   */
  val allTools: StateFlow<List<McpTool>> = _allTools.asStateFlow()

  init {
    // Subscribe to McpToolsProvider extension point changes
    McpToolsProvider.EP.addExtensionPointListener(scope, object : ExtensionPointListener<McpToolsProvider> {
      override fun extensionAdded(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) {
        emitMcpTools("McpToolsProvider extension added")
      }

      override fun extensionRemoved(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) {
        emitMcpTools("McpToolsProvider extension removed")
      }
    })

    // Subscribe to McpToolset extension point changes
    McpToolset.EP.addExtensionPointListener(scope, object : ExtensionPointListener<McpToolset> {
      override fun extensionAdded(extension: McpToolset, pluginDescriptor: PluginDescriptor) {
        emitMcpTools("McpToolset extension added")
      }

      override fun extensionRemoved(extension: McpToolset, pluginDescriptor: PluginDescriptor) {
        emitMcpTools("McpToolset extension removed")
      }
    })
  }

  private fun emitMcpTools(reason: String) {
    logger.trace {
      "Emitting MCP all tools list update: reason=$reason"
    }

    _allTools.value = getAllMcpTools()
  }

  private fun getAllMcpTools(): List<McpTool> {
    val allTools = McpToolsProvider.EP.extensionList.flatMap {
      try {
        it.getTools()
      }
      catch (e: Exception) {
        logger.error("Cannot load tools for $it", e)
        emptyList()
      }
    }
    return allTools
  }
}
