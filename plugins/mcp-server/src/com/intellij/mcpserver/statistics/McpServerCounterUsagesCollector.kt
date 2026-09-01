package com.intellij.mcpserver.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCallResultContent
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.toolwindow.TransportType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.resettableLazy
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType
import kotlinx.coroutines.CoroutineScope

/**
 * Outcome of a single MCP tool call as seen by the server-side call wrapper in `McpSessionHandler`.
 *
 * [SUCCESS] and [RESULT_ERROR] are both "the tool ran to completion": a tool may report a failure to
 * the client by returning `McpToolCallResult.isError` instead of throwing, so the two are
 * distinguished rather than merged into a single boolean.
 */
internal enum class McpToolCallOutcome {
  /** The tool returned normally and did not mark its result as an error. */
  SUCCESS,

  /** The tool returned normally but marked its own result as an error (`McpToolCallResult.isError`). */
  RESULT_ERROR,

  /** The tool signalled an expected, LLM-facing failure via `mcpFail` / `McpExpectedError`. */
  EXPECTED_ERROR,

  /** The tool threw an unexpected exception. */
  FAILURE,

  /** The call was cancelled, typically by a user interaction or a client-side cancellation. */
  CANCELLED,
}

/**
 * Whether the IDE launched the agent behind a call.
 *
 * Derived from `McpSessionOptions.localAgentId`, which the IDE sets when it opens a session for an agent it started.
 * Its absence means a client the IDE did not launch — an agent in a terminal, or another editor pointed at this MCP
 * server — which is why the constant does not claim "terminal".
 */
enum class McpCallerLaunchOrigin {
  IDE_LAUNCHED,
  EXTERNAL_CLIENT,
  UNKNOWN,
}

/**
 * How the reported call arrived. Separate from [McpToolInvocationMode], which selects which tool list a session
 * exposes: a router entry is not a tool list.
 */
enum class McpToolCallInvocationMode {
  /** Called directly, with the router off for this session. */
  DIRECT,

  /** Called directly although the router is on: the client bypassed it. */
  DIRECT_WITH_ROUTER_ENABLED,

  /** Dispatched by the router. */
  VIA_ROUTER,

  /**
   * The `execute_tool` call that carried a dispatch. Exclude it from per-tool counts and latencies: the tool the agent
   * asked for is the [VIA_ROUTER] row, whose duration is nested inside this one.
   */
  ROUTER_ENTRY,
}

/** Why a command passed to `execute_tool` produced no tool call. */
enum class McpDispatchRejectReason {
  /** The command dispatched to a tool; whether that tool then succeeded is `success`. */
  NONE,
  EMPTY_COMMAND,
  UNKNOWN_TOOL,
  MISSING_REQUIRED_PARAMETERS,
  ARGUMENTS_NOT_PARSEABLE,
}

internal enum class LintFilesResultKind {
  CLEAN,
  PROBLEMS_FOUND,
  INCOMPLETE,
}

internal fun lintFilesResultKind(
  problemCount: Int,
  timedOutFileCount: Int,
  notAnalyzedFileCount: Int,
  more: Boolean,
): LintFilesResultKind = when {
  more || timedOutFileCount > 0 || notAnalyzedFileCount > 0 -> LintFilesResultKind.INCOMPLETE
  problemCount > 0 -> LintFilesResultKind.PROBLEMS_FOUND
  else -> LintFilesResultKind.CLEAN
}

/**
 * The size both call paths report as `result_bytes`: the characters of the returned text content, summed without
 * building one string, so a large result is not copied for the sake of measuring it. Structured content is not
 * counted — it is a separate channel, and including it would make the number mean two different things depending on
 * which tool answered.
 */
internal fun McpToolCallResult.reportableResultSize(): Int =
  content.sumOf { part -> (part as? McpToolCallResultContent.Text)?.text?.length ?: 0 }

object McpServerCounterUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("mcpserver.events", 10)

  private val TOOL_NAME = EventFields.StringValidatedByCustomRule<McpToolNameValidator>("tool_name")
  private val TOOLSET = EventFields.StringValidatedByCustomRule<McpToolsetNameValidator>(
    "toolset",
    "The toolset the called tool belongs to. Usage has to be answerable per toolset, not only per tool: there are " +
    "dozens of toolsets and which of them are worth shipping is the question this data exists to answer",
  )
  private val OUTCOME = EventFields.Enum("outcome", McpToolCallOutcome::class.java)

  private val KNOWN_CLIENT_NAMES: List<String> = listOf(
    "codex",
    "codex-cli",
    "codex-acp",
    "codex-mcp-client",
    "claude-code",
    "Copilot MCP Gateway",
    "claude-agent",
    "cursor",
    "cursor-cli",
    "cursor-acp",
    "copilot",
    "copilot-cli",
    "ijproxy",
    "ij-proxy",
    "unknown",
  )

  private val CLIENT_NAME = EventFields.String("client_name", KNOWN_CLIENT_NAMES)
  private val CLIENT_VERSION = EventFields.StringValidatedByRegexpReference("client_version", "version")
  private val TRANSPORT_TYPE = EventFields.Enum<TransportType>("transport_type")
  private val HAS_LOCAL_AGENT = EventFields.Boolean("has_local_agent")
  private val TOOLS_COUNT = EventFields.RoundedInt("tools_count")

  private val LAUNCH_ORIGIN = EventFields.Enum(
    "launch_origin",
    McpCallerLaunchOrigin::class.java,
    "Whether the IDE launched the agent that made this call. An external client is observed only through its MCP " +
    "calls, so without this field a partial session is averaged in as a complete one",
  )
  private val INVOCATION_MODE = EventFields.Enum(
    "invocation_mode",
    McpToolCallInvocationMode::class.java,
    "How the call arrived: directly, dispatched by the universal router, or as the router entry that carried a " +
    "dispatch. A routed call produces both a ROUTER_ENTRY row and a VIA_ROUTER row, so a per-tool count excludes " +
    "ROUTER_ENTRY",
  )
  private val ARGUMENT_BYTES = EventFields.RoundedInt("argument_bytes", "Rounded size of the serialized arguments")
  private val RESULT_BYTES = EventFields.RoundedInt("result_bytes", "Rounded size of the serialized result")

  private val MCP_TOOL_CALL_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "mcp.tool.call",
    TOOL_NAME,
    TOOLSET,
    OUTCOME,
    LAUNCH_ORIGIN,
    INVOCATION_MODE,
    CLIENT_NAME,
    TRANSPORT_TYPE,
    ARGUMENT_BYTES,
    RESULT_BYTES,
    EventFields.DurationMs,
  )

  private val DISPATCHED_TOOL_NAME = EventFields.StringValidatedByCustomRule<McpToolNameValidator>("dispatched_tool_name")
  private val ARG_COUNT = EventFields.Int("arg_count")
  private val DISPATCHED_TOOL_FOUND = EventFields.Boolean("dispatched_tool_found")
  private val SUCCESS = EventFields.Boolean("success")

  private val DISPATCH_REJECT_REASON = EventFields.Enum(
    "dispatch_reject_reason",
    McpDispatchRejectReason::class.java,
    "Why a dispatched command produced no tool call. Before this field the reason was only inferable from a " +
    "validation sentinel in dispatched_tool_name, which is not a value the IDE controls",
  )

  private val EXECUTE_TOOL_DISPATCH_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "mcp.execute_tool.dispatch",
    DISPATCHED_TOOL_NAME,
    ARG_COUNT,
    DISPATCHED_TOOL_FOUND,
    SUCCESS,
    DISPATCH_REJECT_REASON,
    EventFields.DurationMs,
  )

  private val LINT_FILES_MIN_SEVERITY = EventFields.String(
    "min_severity",
    listOf("warning", "strong_warning", "error"),
  )
  private val LINT_FILES_RESULT = EventFields.Enum("result", LintFilesResultKind::class.java)
  private val REQUESTED_FILE_COUNT = EventFields.RoundedInt("requested_file_count")
  private val PROBLEM_FILE_COUNT = EventFields.RoundedInt("problem_file_count")
  private val PROBLEM_COUNT = EventFields.RoundedInt("problem_count")
  private val ERROR_COUNT = EventFields.RoundedInt("error_count")
  private val TIMED_OUT_FILE_COUNT = EventFields.RoundedInt("timed_out_file_count")
  private val NOT_ANALYZED_FILE_COUNT = EventFields.RoundedInt("not_analyzed_file_count")
  private val MORE = EventFields.Boolean("more")

  private val LINT_FILES_FINISHED_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "mcp.lint.files.finished",
    LINT_FILES_MIN_SEVERITY,
    LINT_FILES_RESULT,
    REQUESTED_FILE_COUNT,
    PROBLEM_FILE_COUNT,
    PROBLEM_COUNT,
    ERROR_COUNT,
    TIMED_OUT_FILE_COUNT,
    NOT_ANALYZED_FILE_COUNT,
    MORE,
    EventFields.DurationMs,
  )


  private val SESSION_STARTED_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "mcp.session.started",
    CLIENT_NAME,
    CLIENT_VERSION,
    TRANSPORT_TYPE,
    HAS_LOCAL_AGENT,
    TOOLS_COUNT,
  )

  private val SESSION_FINISHED_EVENT: VarargEventId = GROUP.registerVarargEvent(
    "mcp.session.finished",
    CLIENT_NAME,
    TRANSPORT_TYPE,
    EventFields.DurationMs,
  )

  override fun getGroup(): EventLogGroup = GROUP

  internal fun logMcpToolCall(
    descriptor: McpToolDescriptor,
    outcome: McpToolCallOutcome,
    durationMs: Long,
    invocationMode: McpToolCallInvocationMode,
    launchOrigin: McpCallerLaunchOrigin,
    clientName: String?,
    transportType: TransportType?,
    argumentBytes: Int?,
    resultBytes: Int?,
  ) {
    MCP_TOOL_CALL_EVENT.log(
      buildList {
        add(TOOL_NAME.with(descriptor.name))
        add(TOOLSET.with(descriptor.category.fullyQualifiedName))
        add(OUTCOME.with(outcome))
        add(LAUNCH_ORIGIN.with(launchOrigin))
        add(INVOCATION_MODE.with(invocationMode))
        add(EventFields.DurationMs.with(durationMs))
        // Left out rather than guessed: a dispatched call has no session of its own to read the caller from.
        clientName?.let { add(CLIENT_NAME.with(it)) }
        transportType?.let { add(TRANSPORT_TYPE.with(it)) }
        argumentBytes?.let { add(ARGUMENT_BYTES.with(it)) }
        resultBytes?.let { add(RESULT_BYTES.with(it)) }
      }
    )
  }

  fun logExecuteToolDispatch(
    dispatchedToolName: String?,
    argCount: Int,
    found: Boolean,
    success: Boolean,
    rejectReason: McpDispatchRejectReason,
    durationMs: Long,
  ) {
    EXECUTE_TOOL_DISPATCH_EVENT.log(
      buildList {
        // The name is reported only once it resolved to a tool that exists. What the agent typed is its own text,
        // and putting it here is what made the validator write a sentinel into this field.
        dispatchedToolName?.let { add(DISPATCHED_TOOL_NAME.with(it)) }
        add(ARG_COUNT.with(argCount))
        add(DISPATCHED_TOOL_FOUND.with(found))
        add(SUCCESS.with(success))
        add(DISPATCH_REJECT_REASON.with(rejectReason))
        add(EventFields.DurationMs.with(durationMs))
      }
    )
  }

  fun logLintFilesFinished(
    project: Project,
    minSeverity: String,
    requestedFileCount: Int,
    problemFileCount: Int,
    problemCount: Int,
    errorCount: Int,
    timedOutFileCount: Int,
    notAnalyzedFileCount: Int,
    more: Boolean,
    durationMs: Long,
  ) {
    val result = lintFilesResultKind(problemCount, timedOutFileCount, notAnalyzedFileCount, more)
    LINT_FILES_FINISHED_EVENT.log(
      project,
      LINT_FILES_MIN_SEVERITY.with(minSeverity),
      LINT_FILES_RESULT.with(result),
      REQUESTED_FILE_COUNT.with(requestedFileCount),
      PROBLEM_FILE_COUNT.with(problemFileCount),
      PROBLEM_COUNT.with(problemCount),
      ERROR_COUNT.with(errorCount),
      TIMED_OUT_FILE_COUNT.with(timedOutFileCount),
      NOT_ANALYZED_FILE_COUNT.with(notAnalyzedFileCount),
      MORE.with(more),
      EventFields.DurationMs.with(durationMs),
    )
  }

  fun logSessionStarted(
    clientName: String,
    clientVersion: String,
    transport: TransportType,
    hasLocalAgent: Boolean,
    toolsCount: Int,
  ) {
    SESSION_STARTED_EVENT.log(
      CLIENT_NAME.with(clientName),
      CLIENT_VERSION.with(clientVersion),
      TRANSPORT_TYPE.with(transport),
      HAS_LOCAL_AGENT.with(hasLocalAgent),
      TOOLS_COUNT.with(toolsCount),
    )
  }

  fun logSessionFinished(clientName: String, transport: TransportType, durationMs: Long) {
    SESSION_FINISHED_EVENT.log(
      CLIENT_NAME.with(clientName),
      TRANSPORT_TYPE.with(transport),
      EventFields.DurationMs.with(durationMs),
    )
  }

  internal class McpToolNameValidator : CustomValidationRule() {
    override fun doValidate(data: String, context: IEventContext): ValidationResultType {
      for ((ext, tools) in service<ScopeHolder>().valueMap.value) {
        if (tools.contains(data)) {
          return if (getPluginInfo(ext.javaClass).isSafeToReport()) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
        }
      }
      return ValidationResultType.REJECTED
    }

    override fun getRuleId(): String = "tool_name_validator_id"
  }

  /**
   * Accepts a toolset name the same way [McpToolNameValidator] accepts a tool name: from the tools actually
   * registered, rather than from a list someone has to maintain. A toolset added by a plugin that is not safe to
   * report is reported as third party, and a name no registered tool belongs to is rejected.
   */
  internal class McpToolsetNameValidator : CustomValidationRule() {
    override fun doValidate(data: String, context: IEventContext): ValidationResultType {
      for ((ext, toolsets) in service<ScopeHolder>().toolsetMap.value) {
        if (toolsets.contains(data)) {
          return if (getPluginInfo(ext.javaClass).isSafeToReport()) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
        }
      }
      return ValidationResultType.REJECTED
    }

    override fun getRuleId(): String = "toolset_name_validator_id"
  }

  @Service(Service.Level.APP)
  private class ScopeHolder(coroutineScope: CoroutineScope) {
    @JvmField
    val valueMap = resettableLazy {
      McpToolsProvider.EP.extensionList.associateWith { ext -> ext.getTools().asSequence().map { it.descriptor.name } }
    }

    @JvmField
    val toolsetMap = resettableLazy {
      McpToolsProvider.EP.extensionList.associateWith { ext ->
        ext.getTools().mapTo(HashSet()) { it.descriptor.category.fullyQualifiedName }
      }
    }

    init {
      McpToolsProvider.EP.addChangeListener(coroutineScope) { reset() }
      McpToolset.EP.addChangeListener(coroutineScope) { reset() }
    }

    private fun reset() {
      valueMap.reset()
      toolsetMap.reset()
    }
  }
}

fun logLintFilesFinished(
  project: Project,
  minSeverity: String,
  requestedFileCount: Int,
  problemFileCount: Int,
  problemCount: Int,
  errorCount: Int,
  timedOutFileCount: Int,
  notAnalyzedFileCount: Int,
  more: Boolean,
  durationMs: Long,
) {
  McpServerCounterUsagesCollector.logLintFilesFinished(
    project = project,
    minSeverity = minSeverity,
    requestedFileCount = requestedFileCount,
    problemFileCount = problemFileCount,
    problemCount = problemCount,
    errorCount = errorCount,
    timedOutFileCount = timedOutFileCount,
    notAnalyzedFileCount = notAnalyzedFileCount,
    more = more,
    durationMs = durationMs,
  )
}
