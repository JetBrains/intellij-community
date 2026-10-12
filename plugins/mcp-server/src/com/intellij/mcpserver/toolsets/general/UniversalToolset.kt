@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolCallId
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.McpServerJson
import com.intellij.mcpserver.mcpCallInfo
import com.intellij.mcpserver.mcpCallOwnerIdsOf
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.mcpToolCallIdOrNull
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
private const val MAX_TOOL_NAME_SUGGESTIONS = 5

private const val LIMIT_PROPERTY = "limit"
private const val OFFSET_PROPERTY = "offset"

/** Folded flag names for the last line to read. [UniversalToolset.parseArgsToJson] converts them into a limit. */
private val LAST_LINE_FLAGS = setOf("endline", "lineend")

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
    val dispatchEvent = ExecuteToolDispatchEvent(currentCoroutineContext().mcpToolCallIdOrNull)
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
    // No session: the host runs the call outside an MCP session, for example as an LSP command.
    val sessionHandler = currentCoroutineContext().mcpCallInfo.sessionHandler
                         ?: return McpServerService.getInstanceAsync().getMcpToolsAsync(useFiltersFromEP = false)
    val directTools = sessionHandler.toolsProvider.mcpTools.value
    val routerTools = sessionHandler.routerToolsProvider.mcpTools.value
    return (directTools + routerTools).distinctBy { it.descriptor.name }
  }

  private fun findTool(name: String, all: List<McpToolDef>, dispatchEvent: ExecuteToolDispatchEvent): McpToolDef {
    LOG.trace { "Available tools: ${all.map { it.descriptor.name }.sorted().joinToString(", ")}" }
    val tool = all.find { it.descriptor.name == name }
    if (tool != null) return tool
    dispatchEvent.recordReject(McpDispatchRejectReason.UNKNOWN_TOOL)
    val suggestions = nearestToolNames(name, all)
    if (suggestions.isEmpty()) mcpFail("Tool '$name' not found")
    mcpFail("Tool '$name' not found. Did you mean: ${suggestions.joinToString(", ")}?")
  }

  /** Ranks the known tool names by shared name fragments, closest first. */
  private fun nearestToolNames(name: String, all: List<McpToolDef>): List<String> {
    val wanted = name.lowercase().split('_', '-').filter { it.isNotEmpty() }.toSet()
    if (wanted.isEmpty()) return emptyList()
    return all.asSequence()
      .map { it.descriptor.name }
      .map { candidate ->
        val parts = candidate.lowercase().split('_', '-').filter { it.isNotEmpty() }.toSet()
        candidate to parts.count { part -> wanted.any { it == part || it.contains(part) || part.contains(it) } }
      }
      .filter { (_, score) -> score > 0 }
      .sortedByDescending { (_, score) -> score }
      .map { (candidate, _) -> candidate }
      .take(MAX_TOOL_NAME_SUGGESTIONS)
      .toList()
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
      val known = tool.descriptor.inputSchema.propertiesSchema.keys.joinToString(", ") { "--$it" }
      mcpFail("Missing required parameters: ${missing.joinToString(", ")}. Accepted: $known")
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
      // The call can end by cancellation, so report from a non-cancellable context. A cancelled context would
      // fail the context read and drop the row.
      withContext(NonCancellable) {
        val callInfo = currentCoroutineContext().mcpCallInfo
        McpServerCounterUsagesCollector.logMcpToolCall(
          toolCallId = callInfo.toolCallId,
          ownerIds = mcpCallOwnerIdsOf(callInfo.mcpSessionOptions),
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
  }

  @VisibleForTesting
  internal fun parseArgsToJson(args: List<String>, propertiesSchema: JsonObject): JsonObject {
    val resolvedArgs = LinkedHashMap<String, String>()
    val unresolvedArgs = LinkedHashMap<String, String>()
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
      val resolved = resolveFlagName(name, propertiesSchema)
      if (resolved != null) resolvedArgs[resolved] = value else unresolvedArgs[name] = value
      i += 2
    }

    convertLastLineToLimit(resolvedArgs, unresolvedArgs, propertiesSchema)

    // An empty schema has nothing to check a flag against, so it passes all flags through.
    if (unresolvedArgs.isNotEmpty() && propertiesSchema.isNotEmpty()) {
      val known = propertiesSchema.keys.joinToString(", ") { "$FLAG_PREFIX$it" }
      mcpFail("Unknown parameter '$FLAG_PREFIX${unresolvedArgs.keys.first()}'. Accepted: $known")
    }

    return buildJsonObject {
      for ((key, value) in resolvedArgs) put(key, convertToJsonValue(key, value, propertiesSchema))
      for ((key, value) in unresolvedArgs) put(key, convertToJsonValue(key, value, propertiesSchema))
    }
  }

  /**
   * Converts a last-line flag such as `--end_line` into the `limit` line count.
   *
   * Applies only to a tool that takes both `offset` and `limit`, because the `search_file` limit counts hits.
   */
  private fun convertLastLineToLimit(
    resolvedArgs: MutableMap<String, String>,
    unresolvedArgs: MutableMap<String, String>,
    propertiesSchema: JsonObject,
  ) {
    if (!propertiesSchema.containsKey(LIMIT_PROPERTY) || !propertiesSchema.containsKey(OFFSET_PROPERTY)) return
    val lastLineFlag = unresolvedArgs.keys.firstOrNull { foldFlagName(it) in LAST_LINE_FLAGS } ?: return
    if (resolvedArgs.containsKey(LIMIT_PROPERTY)) {
      mcpFail(
        "Pass either '$FLAG_PREFIX$LIMIT_PROPERTY' (a line count) or " +
        "'$FLAG_PREFIX$lastLineFlag' (a last line), not both."
      )
    }
    val lastLine = unresolvedArgs.getValue(lastLineFlag).toLongOrNull()
    val offset = resolvedArgs[OFFSET_PROPERTY]?.toLongOrNull() ?: 1L
    if (lastLine == null || lastLine < offset) {
      mcpFail(
        "Parameter '$FLAG_PREFIX$lastLineFlag' must be a line number at or after the offset ($offset)."
      )
    }
    resolvedArgs[LIMIT_PROPERTY] = (lastLine - offset + 1).toString()
    unresolvedArgs.remove(lastLineFlag)
  }

  /**
   * Returns the schema property for a flag, or `null`.
   *
   * The order is: the exact name, then the only property with the same folded name.
   * A folded name is lowercase, without `_` and `-`.
   */
  @VisibleForTesting
  internal fun resolveFlagName(name: String, propertiesSchema: JsonObject): String? {
    if (propertiesSchema.containsKey(name)) return name
    val folded = foldFlagName(name)
    return propertiesSchema.keys.singleOrNull { foldFlagName(it) == folded }
  }

  private fun foldFlagName(name: String): String = name.lowercase().replace("_", "").replace("-", "")

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
  private class ExecuteToolDispatchEvent(private val toolCallId: McpToolCallId?) {
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
        toolCallId = toolCallId,
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
