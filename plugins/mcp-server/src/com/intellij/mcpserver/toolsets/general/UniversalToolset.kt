@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.impl.util.McpServerJson
import com.intellij.mcpserver.mcpCallInfo
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.reportToolActivity
import com.intellij.mcpserver.statistics.McpServerCounterUsagesCollector
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportProgressScope
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.execution.ParametersListUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.annotations.VisibleForTesting
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import com.intellij.mcpserver.McpTool as McpToolDef

private const val FLAG_PREFIX = "--"

class UniversalToolset : McpToolset {

  //@McpTool
  //@McpDescription(
  //  """
  //    Simulates a slow operation and reports multiple progress updates.
  //    Use this tool to manually verify MCP progress notifications and keep-alive behavior.
  //    Set `use_background_progress=true` to route progress through `withBackgroundProgress`.
  //  """
  //)
  // private
  suspend fun simulate_progress(
    @McpDescription("Number of progress steps to emit. Must be positive.")
    step_count: Int = 50,
    @McpDescription("Delay in milliseconds between progress updates. Must be non-negative.")
    delay_ms: Int = 1500,
    @McpDescription("Whether to wrap the simulation into withBackgroundProgress.")
    use_background_progress: Boolean = false,
  ): SimulatedProgressResult {
    if (step_count <= 0) {
      mcpFail("`step_count` must be positive")
    }
    if (delay_ms < 0) {
      mcpFail("`delay_ms` must be non-negative")
    }

    currentCoroutineContext().reportToolActivity(
      McpServerBundle.message("tool.activity.simulating.progress", step_count, delay_ms, use_background_progress)
    )

    if (use_background_progress) {
      withBackgroundProgress(
        currentCoroutineContext().project,
        McpServerBundle.message("progress.title.simulating.progress"),
        cancellable = true,
      ) {
        runProgressSimulation(step_count, delay_ms)
      }
    }
    else {
      runProgressSimulation(step_count, delay_ms)
    }

    return SimulatedProgressResult(
      stepCount = step_count,
      delayMs = delay_ms,
      usedBackgroundProgress = use_background_progress,
    )
  }

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

  @VisibleForTesting
  internal fun parseArgsToJson(args: List<String>, propertiesSchema: JsonObject): JsonObject = buildJsonObject {
    var i = 0
    while (i < args.size) {
      val arg = args[i]
      if (!arg.startsWith(FLAG_PREFIX)) {
        mcpFail(
          "Invalid argument format: '$arg'. Expected '${FLAG_PREFIX}paramName value' format. " +
          "For object/array parameters pass a JSON value, e.g. --findings '[{...}]'."
        )
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
      "array" -> parseAsStructuredJson(paramName, value, "array") { it is JsonArray }
      "object" -> parseAsStructuredJson(paramName, value, "object") { it is JsonObject }
      else -> JsonPrimitive(value)
    }
  }

  private fun parseAsStructuredJson(
    paramName: String,
    value: String,
    typeName: String,
    predicate: (JsonElement) -> Boolean,
  ): JsonElement {
    val parsed = try {
      McpServerJson.parseToJsonElement(value)
    }
    catch (e: SerializationException) {
      mcpFail("Parameter '$paramName' expects a JSON $typeName, got: $value (${e.message})")
    }
    if (!predicate(parsed)) {
      mcpFail("Parameter '$paramName' expects a JSON $typeName, got ${parsed::class.simpleName}: $value")
    }
    return parsed
  }

  /**
   * Accumulates state for a single `execute_tool` invocation and emits it as a
   * [McpServerCounterUsagesCollector.EXECUTE_TOOL_DISPATCH_EVENT] FUS event via [emit].
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

  private suspend fun runProgressSimulation(stepCount: Int, delayMs: Int) {
    withProgressText(McpServerBundle.message("progress.title.simulating.progress")) {
      reportProgressScope(size = stepCount) { reporter ->
        repeat(stepCount) { index ->
          val stepNumber = index + 1
          reporter.itemStep {
            coroutineToIndicator { indicator ->
              indicator.text = McpServerBundle.message("progress.title.simulating.progress")
              indicator.text2 = McpServerBundle.message("progress.details.simulating.progress.step", stepNumber, stepCount)
              indicator.fraction = 1.0
            }
            delay(delayMs.milliseconds)
          }
        }
      }
    }
  }

  @Serializable
  data class SimulatedProgressResult(
    val stepCount: Int,
    val delayMs: Int,
    val usedBackgroundProgress: Boolean,
  )
}
