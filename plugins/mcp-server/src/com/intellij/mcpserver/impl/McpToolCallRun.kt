// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpCallInfo
import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCallResultContent
import com.intellij.mcpserver.ToolCallListener
import com.intellij.mcpserver.launchOriginOf
import com.intellij.mcpserver.mcpCallOwnerIdsOf
import com.intellij.mcpserver.statistics.McpServerCounterUsagesCollector
import com.intellij.mcpserver.statistics.McpToolCallInvocationMode
import com.intellij.mcpserver.statistics.McpToolCallOutcome
import com.intellij.mcpserver.statistics.reportableResultSize
import com.intellij.mcpserver.toolsets.general.ROUTER_TOOL_NAME
import com.intellij.mcpserver.toolwindow.TransportType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.diagnostic.traceThrowable
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.telemetry.IJNoopTracer
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import com.intellij.util.application
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

private val logger = logger<McpToolCallRun>()

private val MCP_SERVER_TRACER_SCOPE = Scope("mcpServer")

internal fun mcpServerTracer(): IJTracer =
  if (Registry.`is`("mcp.server.ot.trace"))
    TelemetryManager.getInstance().getTracer(MCP_SERVER_TRACER_SCOPE)
  else
    IJNoopTracer

/** The logger owner of [runMcpToolCall]. */
private class McpToolCallRun

/**
 * How a call of [toolName] arrived in a session of [invocationMode]. The router is itself a registered tool, so a routed
 * call passes the wrapper once as `execute_tool` and once, from [com.intellij.mcpserver.toolsets.general.UniversalToolset],
 * as the tool it dispatched to. Reporting both as DIRECT made one routed call look like two ordinary ones.
 */
internal fun callArrivalOf(toolName: String, invocationMode: McpSessionInvocationMode): McpToolCallInvocationMode = when {
  toolName == ROUTER_TOOL_NAME -> McpToolCallInvocationMode.ROUTER_ENTRY
  invocationMode == McpSessionInvocationMode.VIA_ROUTER -> McpToolCallInvocationMode.DIRECT_WITH_ROUTER_ENABLED
  else -> McpToolCallInvocationMode.DIRECT
}

/**
 * Runs one call of [mcpTool] with [arguments], or with none, in the context of [callInfo]. The caller has put [callInfo]
 * in the coroutine context. Both an MCP session and an in-process call run a tool here. The run tells the [ToolCallListener]s, records the
 * side effects, traces the call, and reports `mcp.tool.call` with [arrival], [clientName] and [transportType]. A failure
 * of the tool becomes an error result, and a cancellation too.
 */
internal suspend fun runMcpToolCall(
  mcpTool: McpTool,
  arguments: JsonObject?,
  callInfo: McpCallInfo,
  arrival: McpToolCallInvocationMode,
  clientName: String?,
  transportType: TransportType?,
): McpToolCallResult {
  val attributes = Attributes.builder()
    .put("mcp.tool.name", mcpTool.descriptor.name)
    .put("mcp.client.name", callInfo.clientInfo.name)
    .put("mcp.client.version", callInfo.clientInfo.version)
    .put("mcp.call.id", callInfo.callId.toLong())
    .put("mcp.tool_call.id", callInfo.toolCallId.value)
  callInfo.sessionId?.let { attributes.put("mcp.session.id", it) }
  val span = mcpServerTracer().spanBuilder("mcp.tool.call", TracerLevel.DEFAULT)
    .setAllAttributes(attributes.build())
    .startSpan()

  val callMark = TimeSource.Monotonic.markNow()
  var outcome = McpToolCallOutcome.FAILURE
  // Held for reporting only: an error result is a result too, and its size is what the client received. Stays
  // null when nothing produced one, so an absent size never reads as a result of size zero.
  var reportedResult: McpToolCallResult? = null

  try {
    span.makeCurrent().use {
      try {
        @Suppress("IncorrectCancellationExceptionHandling")
        val toolCallResult = try {
          application.messageBus.syncPublisher(ToolCallListener.TOPIC)
            .beforeMcpToolCall(mcpTool.descriptor, callInfo)

          logger.trace { "Start calling tool '${mcpTool.descriptor.name}'. Arguments: $arguments" }

          span.addEvent(
            "mcp.tool.call.started",
            Attributes.of(
              AttributeKey.stringKey("arguments.size"),
              arguments?.size?.toString() ?: "0"
            )
          )

          val sideEffectResult = processSideEffects(callInfo.callId) {
            mcpTool.call(arguments ?: EmptyJsonObject)
          }

          // A tool may report a failure by returning an error result instead of throwing.
          outcome = if (sideEffectResult.result.isError) McpToolCallOutcome.RESULT_ERROR else McpToolCallOutcome.SUCCESS

          logger.trace {
            "Tool call successful '${mcpTool.descriptor.name}'. Result: ${
              sideEffectResult.result.content.joinToString("\n") { it.toString() }
            }"
          }

          span.addEvent(
            "mcp.tool.call.completed",
            Attributes.of(
              AttributeKey.stringKey("result.content.count"),
              sideEffectResult.result.content.size.toString()
            )
          )
          span.setStatus(StatusCode.OK)
          span.setAllAttributes(
            Attributes.builder()
              .put("mcp.side_effects.vfs_events", sideEffectResult.vfsEventCount.toLong())
              .put("mcp.side_effects.document_changes", sideEffectResult.documentChangeCount.toLong())
              .build()
          )

          application.messageBus.syncPublisher(ToolCallListener.TOPIC)
            .afterMcpToolCall(mcpTool.descriptor, sideEffectResult.events, null, callInfo, sideEffectResult.result.deepCopy())
          sideEffectResult.result
        }
        catch (ce: CancellationException) {
          outcome = McpToolCallOutcome.CANCELLED
          val message = "MCP tool call has been cancelled likely by a user interaction: ${ce.message}"
          logger.traceThrowable { CancellationException(message, ce) }
          span.setStatus(StatusCode.ERROR, message)
          application.messageBus.syncPublisher(ToolCallListener.TOPIC)
            .afterMcpToolCall(mcpTool.descriptor, emptyList(), ce, callInfo)
          McpToolCallResult.error(message)
        }
        catch (mcpException: McpExpectedError) {
          outcome = McpToolCallOutcome.EXPECTED_ERROR
          logger.traceThrowable { mcpException }
          span.setStatus(StatusCode.ERROR, "MCP expected error: ${mcpException.mcpErrorText}")
          application.messageBus.syncPublisher(ToolCallListener.TOPIC)
            .afterMcpToolCall(mcpTool.descriptor, emptyList(), mcpException, callInfo)
          McpToolCallResult.error(mcpException.mcpErrorText, mcpException.mcpErrorStructureContent)
        }
        catch (t: Throwable) {
          outcome = McpToolCallOutcome.FAILURE
          val errorMessage = "MCP tool call has been failed: ${t.message}"
          logger.error(t)
          span.setStatus(StatusCode.ERROR, errorMessage)
          application.messageBus.syncPublisher(ToolCallListener.TOPIC)
            .afterMcpToolCall(mcpTool.descriptor, emptyList(), t, callInfo)
          McpToolCallResult.error(errorMessage)
        }
        reportedResult = toolCallResult
        return toolCallResult
      }
      finally {
        McpServerCounterUsagesCollector.logMcpToolCall(
          toolCallId = callInfo.toolCallId,
          ownerIds = mcpCallOwnerIdsOf(callInfo.mcpSessionOptions),
          descriptor = mcpTool.descriptor,
          outcome = outcome,
          durationMs = callMark.elapsedNow().inWholeMilliseconds,
          invocationMode = arrival,
          launchOrigin = launchOriginOf(callInfo.mcpSessionOptions),
          clientName = clientName,
          transportType = transportType,
          argumentBytes = arguments?.toString()?.length,
          resultBytes = reportedResult?.reportableResultSize(),
        )
      }
    }
  }
  finally {
    span.end()
  }
}

private fun McpToolCallResult.deepCopy(): McpToolCallResult {
  val copiedContent: Array<McpToolCallResultContent> = content.map { content ->
    when (content) {
      is McpToolCallResultContent.Text -> McpToolCallResultContent.Text(content.text)
    }
  }.toTypedArray()
  val copiedStructuredContent = structuredContent?.let {
    Json.decodeFromString(JsonObject.serializer(), Json.encodeToString(JsonObject.serializer(), it))
  }
  return McpToolCallResult(copiedContent, copiedStructuredContent, isError)
}
