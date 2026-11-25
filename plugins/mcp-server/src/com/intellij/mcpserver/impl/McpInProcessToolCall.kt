// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.concurrency.ExecutionInitiator
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.mcpserver.ClientInfo
import com.intellij.mcpserver.McpCallAdditionalDataElement
import com.intellij.mcpserver.McpCallInfo
import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.elicitation.McpNoElicitationElement
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManager.ConflictResolution
import com.intellij.util.asDisposable
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * See [McpServerService.callTool]. The tools of the call are the tools that a session of [options] lists at the call,
 * read once. The call runs with [runMcpToolCall], as the call of an MCP session does, with no MCP session in its context.
 * So a tool gets no progress notification, and an elicitation of a tool fails, see [McpNoElicitationElement]. While the call runs, a document of the IDE merges a
 * change on the disk, as it does for an MCP session.
 */
internal suspend fun callToolInProcess(
  service: McpServerServiceImpl,
  options: McpServerService.McpSessionOptions,
  projectPath: String,
  clientInfo: ClientInfo,
  toolName: String,
  arguments: JsonObject,
): McpToolCallResult {
  val invocationMode = options.invocationMode ?: McpToolFilterSettings.getInstance().effectiveInvocationMode
  val tools = sessionTools(service, options, invocationMode)
  // A routed tool is reachable only through the router, as in a session, which lists only the direct tools.
  val tool = tools.direct.firstOrNull { it.descriptor.name == toolName } ?: return McpToolCallResult.error("Tool '$toolName' not found")
  val project = try {
    serviceAsync<McpSessionProjectResolver>().resolveSessionProject(
      projectPathFromArgument = null,
      projectPathFromCallHeader = null,
      projectPathFromSessionHeader = projectPath,
      roots = emptySet(),
    )
  }
  catch (e: McpExpectedError) {
    return McpToolCallResult.error(e.mcpErrorText, e.mcpErrorStructureContent)
  }
  catch (t: Throwable) {
    rethrowControlFlowException(t)
    return McpToolCallResult.error(t.message ?: "The project $projectPath of the call does not resolve")
  }
  val callInfo = McpCallInfo(
    callId = McpServerServiceImpl.callId.getAndAdd(1),
    clientInfo = clientInfo,
    project = project,
    mcpToolDescriptor = tool.descriptor,
    rawArguments = arguments,
    meta = EmptyJsonObject,
    mcpSessionOptions = options,
  ).apply {
    routableTools = { tools.routable }
  }
  return coroutineScope {
    FileDocumentManager.getInstance().overrideConflictResolution(ConflictResolution.MERGE, asDisposable())
    withContext(McpCallAdditionalDataElement(callInfo) + McpNoElicitationElement + ExecutionInitiator.MCP.contextElement) {
      runMcpToolCall(
        mcpTool = tool,
        arguments = arguments,
        callInfo = callInfo,
        arrival = callArrivalOf(toolName, invocationMode),
        clientName = clientInfo.name,
        transportType = null,
      )
    }
  }
}

/** The [direct] tools of a session, which it lists, and its [routable] tools, which its router reaches. */
private class SessionTools(@JvmField val direct: List<McpTool>, @JvmField val routable: List<McpTool>)

/** The tools of a session of [options] in [invocationMode], as `McpSessionHandler` reads them. */
private suspend fun sessionTools(
  service: McpServerServiceImpl,
  options: McpServerService.McpSessionOptions,
  invocationMode: McpSessionInvocationMode,
): SessionTools {
  val directMode = if (invocationMode == McpSessionInvocationMode.DIRECT) McpToolInvocationMode.DIRECT else McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED
  val direct = service.getMcpToolsAsync(filter = options.toolFilter, sessionOptions = options, invocationMode = directMode)
  val routed = service.getMcpToolsAsync(filter = options.toolFilter, sessionOptions = options, invocationMode = McpToolInvocationMode.VIA_ROUTER)
  return SessionTools(direct, (direct + routed).distinctBy { it.descriptor.name })
}
