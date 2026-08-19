@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpExpectedError
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
import com.intellij.mcpserver.statistics.reportableResultSize
import com.intellij.mcpserver.launchOriginOf
import com.intellij.mcpserver.statistics.McpDispatchRejectReason
import com.intellij.mcpserver.statistics.McpToolCallInvocationMode
import com.intellij.mcpserver.statistics.McpToolCallOutcome
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportProgressScope
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.execution.ParametersListUtil
import kotlin.coroutines.cancellation.CancellationException
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

private val LOG = fileLogger()

/** The name of the router tool, for the places that have to recognise a call to it rather than to an ordinary tool. */
internal val ROUTER_TOOL_NAME: String = UniversalToolset::execute_tool.name

class UniversalToolset : McpToolset {
  override fun displayName(): String = McpServerBundle.message("toolset.display.name.universal")

  override fun displayDescription(toolName: String): String? = McpServerBundle.message("tool.description.$toolName")

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

      val parsedCommand = parseCommand(command, dispatchEvent).also { dispatchEvent.recordParsed(it.argsCount) }

      val tool = findTool(parsedCommand.toolName, resolveRouterTools(), dispatchEvent)
        .also { dispatchEvent.recordFound(it.descriptor.name) }

      val jsonArgs = buildArguments(tool, parsedCommand.args, dispatchEvent)
      val result = invokeTool(tool, jsonArgs)

      dispatchEvent.recordSuccess()
      return result
    }
    finally {
      dispatchEvent.emit()
    }
  }

  private data class ParsedCommand(val toolName: String, val args: List<String>, val argsCount: Int)

  private fun parseCommand(command: String, dispatchEvent: ExecuteToolDispatchEvent): ParsedCommand {
    val parts = ParametersListUtil.parse(command, false, true)
    if (parts.isEmpty()) {
      dispatchEvent.recordReject(McpDispatchRejectReason.EMPTY_COMMAND)
      mcpFail("Command is empty")
    }
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
    val directTools = sessionHandler.toolsProvider.mcpTools.value
    val routerTools = sessionHandler.routerToolsProvider.mcpTools.value
    return (directTools + routerTools).distinctBy { it.descriptor.name }
  }

  private fun findTool(name: String, all: List<McpToolDef>, dispatchEvent: ExecuteToolDispatchEvent): McpToolDef {
    LOG.trace { "Available tools: ${all.map { it.descriptor.name }.sorted().joinToString(", ")}" }
    val tool = all.find { it.descriptor.name == name }
    if (tool == null) {
      dispatchEvent.recordReject(McpDispatchRejectReason.UNKNOWN_TOOL)
      mcpFail("Tool '$name' not found")
    }
    return tool
  }

  private fun buildArguments(tool: McpToolDef, rawArgs: List<String>, dispatchEvent: ExecuteToolDispatchEvent): JsonObject {
    val jsonArgs = try {
      parseArgsToJson(rawArgs, tool.descriptor.inputSchema.propertiesSchema)
    }
    catch (e: Throwable) {
      dispatchEvent.recordReject(McpDispatchRejectReason.ARGUMENTS_NOT_PARSEABLE)
      throw e
    }
    val missing = tool.descriptor.inputSchema.requiredProperties.filter { it !in jsonArgs }
    if (missing.isNotEmpty()) {
      dispatchEvent.recordReject(McpDispatchRejectReason.MISSING_REQUIRED_PARAMETERS)
      mcpFail("Missing required parameters: ${missing.joinToString(", ")}")
    }
    return jsonArgs
  }

  /**
   * Calls the dispatched tool and reports it as a tool call in its own right. The call goes straight to
   * [McpToolDef.call], bypassing the wrapper in `McpSessionHandler` that reports `mcp.tool.call`, so without this a
   * routed call produced a dispatch row and no call row.
   */
  private suspend fun invokeTool(tool: McpToolDef, jsonArgs: JsonObject): String {
    val callMark = TimeSource.Monotonic.markNow()
    var outcome = McpToolCallOutcome.SUCCESS
    var resultBytes: Int? = null
    try {
      val result = tool.call(jsonArgs)
      resultBytes = result.reportableResultSize()
      if (result.isError) {
        outcome = McpToolCallOutcome.RESULT_ERROR
        mcpFail("Tool execution failed: $result")
      }
      return result.toString()
    }
    catch (e: McpExpectedError) {
      if (outcome == McpToolCallOutcome.SUCCESS) outcome = McpToolCallOutcome.EXPECTED_ERROR
      throw e
    }
    catch (e: CancellationException) {
      outcome = McpToolCallOutcome.CANCELLED
      throw e
    }
    catch (e: Throwable) {
      outcome = McpToolCallOutcome.FAILURE
      throw e
    }
    finally {
      val callInfo = currentCoroutineContext().mcpCallInfo
      McpServerCounterUsagesCollector.logMcpToolCall(
        descriptor = tool.descriptor,
        outcome = outcome,
        durationMs = callMark.elapsedNow().inWholeMilliseconds,
        invocationMode = McpToolCallInvocationMode.VIA_ROUTER,
        launchOrigin = launchOriginOf(callInfo.mcpSessionOptions),
        clientName = callInfo.clientInfo.name,
        transportType = null,
        argumentBytes = jsonArgs.toString().length,
        // Absent when the call threw: there is no result to size, which is not the same as a result of size zero.
        resultBytes = resultBytes,
      )
    }
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
      "object" -> parseAsStructuredJson(paramName, value, "object") { it is JsonObject || it is JsonArray }
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
    private var toolName: String? = null
    private var argCount: Int = 0
    private var found: Boolean = false
    private var success: Boolean = false
    private var rejectReason: McpDispatchRejectReason = McpDispatchRejectReason.NONE

    fun recordParsed(argCount: Int) {
      this.argCount = argCount
    }

    /** Called once the name resolved to a tool that exists, so the field never carries what the agent typed. */
    fun recordFound(toolName: String) {
      this.toolName = toolName
      found = true
    }

    fun recordReject(reason: McpDispatchRejectReason) {
      rejectReason = reason
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
        rejectReason = rejectReason,
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
