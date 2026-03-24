package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.impl.util.asTools
import com.intellij.openapi.diagnostic.logger

private val logger = logger<ReflectionToolsProvider>()

class ReflectionToolsProvider : McpToolsProvider {
  override fun getTools(): List<McpTool> {
    return McpToolset.enabledToolsets.flatMap { toolset ->
      try {
        toolset.asTools()
      }
      catch (e: Exception) {
        logger.warn("Cannot load tools for $toolset", e)
        emptyList<McpTool>()
      }
    }
  }
}