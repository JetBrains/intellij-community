package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Extension point to provide custom MCP tools.
 *
 * Better to use [McpToolset] extension point instead for easier definition of MCP tools.
 */
interface McpToolsProvider {
  companion object {
    val EP: ExtensionPointName<McpToolsProvider> = ExtensionPointName.create<McpToolsProvider>("com.intellij.mcpServer.mcpToolsProvider")
  }

  /**
   * Returns a list of MCP tools.
   */
  fun getTools(): List<McpTool>
}