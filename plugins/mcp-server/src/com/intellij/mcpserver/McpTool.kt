package com.intellij.mcpserver

import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.CoroutineContext

interface McpTool {

  /**
   * Descriptor of the tool: name, description, and input parameters.
   */
  val descriptor: McpToolDescriptor

  /**
   * Logic of the tool.
   *
   * Project can be obtained via [CoroutineContext.project] or [CoroutineContext.projectOrNull] extension, but it may be null
   */
  suspend fun call(args: JsonObject): McpToolCallResult
}

sealed interface McpToolCallResultContent {
  class Text(val text: String) : McpToolCallResultContent
}

class McpToolCallResult(val content: Array<McpToolCallResultContent>, val isError: Boolean = false) {
  companion object {
    fun error(errorMessage: String): McpToolCallResult = McpToolCallResult(arrayOf(McpToolCallResultContent.Text(errorMessage)), true)
    fun text(text: String): McpToolCallResult = McpToolCallResult(arrayOf(McpToolCallResultContent.Text(text)))
  }
}

open class McpExpectedError(val mcpErrorText: String) : Exception(mcpErrorText)