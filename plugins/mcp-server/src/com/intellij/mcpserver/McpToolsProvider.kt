package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extension point to provide custom MCP tools.
 *
 * Better to use [McpToolset] extension point instead for easier definition of MCP tools.
 *
 * For a comprehensive authoring guide, see the [MCP Server README](../../../../README.md).
 */
interface McpToolsProvider {
  companion object {
    val EP: ExtensionPointName<McpToolsProvider> = ExtensionPointName.create("com.intellij.mcpServer.mcpToolsProvider")
  }

  /**
   * Returns a list of MCP tools.
   *
   * Building the tools is slow, so this must not be called on the EDT. Prefer [getToolsAsync].
   */
  @RequiresBackgroundThread
  fun getTools(): List<McpTool>

  /**
   * Returns a list of MCP tools, allowing the implementation to build them concurrently.
   *
   * This is the preferred entry point: everything that can trigger the initial tool list computation reaches it from a
   * coroutine, so the reflection-heavy conversion never blocks the EDT (IJPL-251556). Defaults to [getTools].
   */
  suspend fun getToolsAsync(): List<McpTool> = withContext(Dispatchers.Default) {
    getTools()
  }
}
