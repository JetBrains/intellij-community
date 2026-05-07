@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.impl.util.McpServerJson
import com.intellij.mcpserver.mcpCallInfo
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.statistics.McpServerCounterUsagesCollector
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.TimeSource
import com.intellij.mcpserver.McpTool as McpToolDef

private const val FLAG_PREFIX = "--"

class UniversalToolset : McpToolset {
  @McpTool
  @McpDescription("""Universal tool executor that can invoke specific IDE MCP tool dynamically.""")
  suspend fun execute_tool(
    @McpDescription("Command-line string with tool name and arguments")
    command: String,
  ): String {
    val dispatchEvent = ExecuteToolDispatchEvent()
    try {
      currentCoroutineContext().reportToolActivity(
        McpServerBundle.message("tool.activity.executing.universal.tool", command))

      val parsedCommand = parseCommand(command).also { dispatchEvent.recordParsed(it.toolName, it.argsCount) }

      val tool = findTool(parsedCommand.toolName, resolveRouterTools()).also { dispatchEvent.recordFound() }

      val jsonArgs = buildArguments(tool, parsedCommand.args)
      val result = invokeTool(tool, jsonArgs)

      dispatchEvent.recordSuccess()
      return result
    }
    finally {
      dispatchEvent.emit()
    }
  }

  private data class ParsedCommand(val toolName: String, val args: List<String>, val argsCount: Int)

  private fun parseCommand(command: String): ParsedCommand {
    val parts = ParametersListUtil.parse(command, false, true)
    if (parts.isEmpty()) mcpFail("Command is empty")
    val args = parts.drop(1)
    return ParsedCommand(
      toolName = parts[0],
      args = args,
      argsCount = args.count { it.startsWith(FLAG_PREFIX) },
    )
  }

  private suspend fun resolveRouterTools(): List<McpToolDef> {
    val sessionHandler = currentCoroutineContext().mcpCallInfo.sessionHandler
                         ?: mcpFail("Session handler not available")
    val routerToolsProvider = sessionHandler.routerToolsProvider
                              ?: mcpFail("Router tools provider not available")
    return routerToolsProvider.mcpTools.value
  }

  private fun findTool(name: String, all: List<McpToolDef>): McpToolDef {
    return all.find { it.descriptor.name == name }
           ?: mcpFail("Tool '$name' not found. Available tools: ${all.map { it.descriptor.name }.sorted().joinToString(", ")}")
  }

  private fun buildArguments(tool: McpToolDef, rawArgs: List<String>): JsonObject {
    val jsonArgs = parseArgsToJson(rawArgs, tool.descriptor.inputSchema.propertiesSchema)
    val missing = tool.descriptor.inputSchema.requiredProperties.filter { it !in jsonArgs }
    if (missing.isNotEmpty()) mcpFail("Missing required parameters: ${missing.joinToString(", ")}")
    return jsonArgs
  }

  private suspend fun invokeTool(tool: McpToolDef, jsonArgs: JsonObject): String {
    val result = tool.call(jsonArgs)
    if (result.isError) mcpFail("Tool execution failed: $result")
    return result.toString()
  }

  private fun parseArgsToJson(args: List<String>, propertiesSchema: JsonObject): JsonObject = buildJsonObject {
    var i = 0
    while (i < args.size) {
      val arg = args[i]
      if (!arg.startsWith(FLAG_PREFIX)) {
        mcpFail("Invalid argument format: '$arg'. Expected '${FLAG_PREFIX}paramName value' format")
      }
      val name = arg.substring(FLAG_PREFIX.length)
      val value = args.getOrNull(i + 1) ?: mcpFail("Parameter '$name' requires a value")
      put(name, convertToJsonValue(name, value, propertiesSchema))
      i += 2
    }
  }

  private fun convertToJsonValue(paramName: String, value: String, propertiesSchema: JsonObject): JsonElement {
    val paramSchema = propertiesSchema[paramName] as? JsonObject
    val type = (paramSchema?.get("type") as? JsonPrimitive)?.content ?: "string"
    
    return when (type) {
      "boolean" -> JsonPrimitive(value.toBoolean())
      "integer", "number" -> {
        val numValue = value.toLongOrNull()
        if (numValue != null) {
          JsonPrimitive(numValue)
        } else {
          JsonPrimitive(value.toDouble())
        }
      }
      "array", "object" -> {
        runCatching { McpServerJson.parseToJsonElement(value) }.getOrElse { JsonNull }
      }
      else -> JsonPrimitive(value)
    }
  }

  /**
   * Accumulates state for a single `execute_tool` invocation and emits it as a
   * [McpServerCounterUsagesCollector.ExecuteToolDispatch] FUS event via [emit].
   *
   * Counters are mutated progressively as the dispatch advances, so the event still
   * reports the last reached stage when an `mcpFail` aborts the call midway.
   */
  private class ExecuteToolDispatchEvent {
    private val mark = TimeSource.Monotonic.markNow()
    private var toolName: String = ""
    private var argCount: Int = 0
    private var found: Boolean = false
    private var success: Boolean = false

    fun recordParsed(toolName: String, argCount: Int) {
      this.toolName = toolName
      this.argCount = argCount
    }

    fun recordFound() {
      found = true
    }

    fun recordSuccess() {
      success = true
    }

    fun emit() {
      McpServerCounterUsagesCollector.logExecuteToolDispatch(
        dispatchedToolName = toolName,
        argCount = argCount,
        found = found,
        success = success,
        durationMs = mark.elapsedNow().inWholeMilliseconds,
      )
    }
  }
}
