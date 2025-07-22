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
  class Text(val text: String) : McpToolCallResultContent {
    override fun toString(): String = text
  }
}

class McpToolCallResult(val content: Array<McpToolCallResultContent>, val structuredContent: JsonObject? = null, val isError: Boolean = false) {
  companion object {
    fun error(errorMessage: String): McpToolCallResult = McpToolCallResult(content = arrayOf(McpToolCallResultContent.Text(errorMessage)),
                                                                           structuredContent = null,
                                                                           isError = true)
    fun text(text: String, structuredContent: JsonObject? = null): McpToolCallResult = McpToolCallResult(content = arrayOf(McpToolCallResultContent.Text(text)),
                                                                                                         structuredContent = structuredContent)
  }
  override fun toString(): String {
    val result = content.joinToString("\n")
    if (isError) return "[error]: $result"
    return result
  }
}

open class McpExpectedError(val mcpErrorText: String) : Exception(mcpErrorText)

/**
 * Throws [McpExpectedError] with [message]
 *
 * The exception is caught by MCP server and returned to client as a well-rendered error
 */
fun mcpFail(message: String): Nothing = throw McpExpectedError(message)