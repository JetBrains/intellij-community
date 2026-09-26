package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.impl.util.asTools
import com.intellij.mcpserver.impl.util.asToolsAsync
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private val logger = logger<ReflectionToolsProvider>()

class ReflectionToolsProvider : McpToolsProvider {
  override fun getTools(): List<McpTool> {
    return McpToolset.enabledToolsets.flatMap { toolset ->
      try {
        toolset.asTools()
      }
      catch (e: Exception) {
        logConversionFailure(toolset, e)
        emptyList()
      }
    }
  }

  /**
   * Converts the toolsets concurrently: each one on its own, and every tool of a toolset in parallel too.
   */
  override suspend fun getToolsAsync(): List<McpTool> {
    return coroutineScope {
      McpToolset.enabledToolsets.map { toolset ->
        async(Dispatchers.Default) {
          try {
            toolset.asToolsAsync()
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: Exception) {
            logConversionFailure(toolset, e)
            emptyList()
          }
        }
      }.awaitAll().flatten()
    }
  }
}

// a broken toolset must not hide the tools of all the other ones
private fun logConversionFailure(toolset: McpToolset, e: Exception) {
  logger.warn("Cannot load tools for $toolset", e)
}
