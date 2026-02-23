package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName
import kotlin.coroutines.CoroutineContext

/**
 * Marker interface for MCP toolset
 *
 * You may inherit it and define your tools as ordinary Kotlin methods.
 *
 * Every tool should be marked with [com.intellij.mcpserver.annotations.McpTool] annotation to be discovered and registered.
 *
 * Descriptions for tools, parameters, or type members may be provided with [com.intellij.mcpserver.annotations.McpDescription] annotation.
 *
 * Parameter should be either primitive types or serializable by `kotlinx.serialization`
 *
 * As a return value you can use one of the following:
 * * null of any type - rendered as `[null]`
 * * primitive type value (Int, Boolean, String, etc.) - rendered as is (by calling `.toString()`)
 * * any serializable value - rendered as JSON by `.encodeToString()`
 * * instance of [McpToolCallResult] - rendered as is
 * * instance of [McpToolCallResultContent] - rendered as is
 * * in the case of `Unit` return type - rendered as `[success]`
 *
 * Optional parameters are supported.
 *
 * At the moment, recursive types are not supported.
 *
 * You may throw [McpExpectedError] to indicate an error to the calling site and preserve the error text.
 * Other exceptions are decorated with `MCP tool call has failed:...`
 *
 * [com.intellij.openapi.project.Project] instance may be obtained via [CoroutineContext.project]
 *
 * @see [com.intellij.mcpserver.impl.util.asTools]
 *
 * ``` Kotlin
 * class MyToolset : McpToolset {
 *     @McpTool
 *     @McpDescription("My best tool overridden description")
 *     fun my_best_tool(arg1: String, arg2: Int) {
 *         // ...
 *     }
 *
 *     @McpTool
 *     @McpDescription("My best tool 2")
 *     fun my_best_tool_2(arg1: String, arg2: Int) {
 *          // ...
 *     }
 * }
 * ```
 */
interface McpToolset {
  companion object {
    val EP: ExtensionPointName<McpToolset> = ExtensionPointName<McpToolset>("com.intellij.mcpServer.mcpToolset")

    val enabledToolsets: List<McpToolset>
      get() = EP.extensionList.filter { it.isEnabled() }
  }

  fun isEnabled(): Boolean = true
}