package com.intellij.mcpserver.impl

import com.intellij.mcpserver.ClientInfo
import com.intellij.mcpserver.McpCallAdditionalDataElement
import com.intellij.mcpserver.McpCallInfo
import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCallResultContent
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.ToolCallListener
import com.intellij.mcpserver.elicitation.McpElicitationKind
import com.intellij.mcpserver.elicitation.McpSessionElement
import com.intellij.mcpserver.impl.util.network.httpRequestOrNull
import com.intellij.mcpserver.impl.util.projectPathParameterName
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.mcpserver.statistics.McpServerCounterUsagesCollector
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.mcpserver.toolwindow.McpDiagnosticService
import com.intellij.mcpserver.toolwindow.TransportType
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.diagnostic.traceThrowable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.telemetry.IJNoopTracer
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.application
import io.ktor.util.toMap
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

private val logger = logger<McpSessionHandler>()

private val structuredToolOutputEnabled get() = Registry.`is`("mcp.server.structured.tool.output")
private val MCP_SERVER_TRACER_SCOPE = Scope("mcpServer")
private fun getTracer(): IJTracer =
  if (Registry.`is`("mcp.server.ot.trace"))
    TelemetryManager.getInstance().getTracer(MCP_SERVER_TRACER_SCOPE)
  else
    IJNoopTracer

/**
 * Helper class that manages MCP tools for a single session.
 * Each session gets its own instance to avoid interference between sessions.
 */
internal class McpSessionHandler(
  parentScope: CoroutineScope,
  private val sessionOptions: McpServerService.McpSessionOptions,
  mcpServerService: McpServerService,
  private val mcpServer: Server,
  private val transportType: TransportType,
  private val projectPathFromInitialRequest: String?,
  private val elicitationKind: McpElicitationKind,
  useFiltersFromEP: Boolean,
) {
  val sessionScope = parentScope.childScope("SessionMcpToolsManager")

  /**
   * The effective invocation mode for this session.
   * Takes the value from sessionOptions if set, otherwise falls back to settings.
   */
  private val invocationMode: McpSessionInvocationMode =
    sessionOptions.invocationMode ?: McpToolFilterSettings.getInstance().invocationMode

  /**
   * The tools provider for direct MCP exposure.
   * - In DIRECT mode: provides tools with McpToolInvocationMode.DIRECT
   * - In VIA_ROUTER mode: provides tools with McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED
   *   (the router tool itself and exception tools that should be exposed directly)
   */
  private val toolsProvider = McpFilteredToolsListProvider(
    sessionScope,
    sessionOptions,
    mcpServerService,
    useFiltersFromEP,
    invocationMode = if (invocationMode == McpSessionInvocationMode.DIRECT)
      McpToolInvocationMode.DIRECT
    else
      McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED
  )

  /**
   * The tools provider for the universal tool (router).
   * Only created when invocationMode is VIA_ROUTER.
   * Contains tools that are invoked via the router rather than directly.
   */
  val routerToolsProvider: McpFilteredToolsListProvider = McpFilteredToolsListProvider(
    sessionScope,
    sessionOptions,
    mcpServerService,
    useFiltersFromEP,
    invocationMode = McpToolInvocationMode.VIA_ROUTER,
  )

  val mcpTools = toolsProvider.mcpTools

  private val sessionAwaiter = CompletableDeferred<ServerSession>()
  private val sessionRoots = AtomicReference<Set<String>?>(null)
  private val resolvedSessionProject = AtomicReference<Project?>(null)

  /**
   * Serializes concurrent [updateTools] invocations (the initial explicit call from [McpServerService],
   * the [mcpTools] flow collector, and [setSessionRoots]) so their remove/add halves cannot interleave
   * and momentarily expose an empty tools list to the client.
   */
  private val updateMutex = Mutex()

  /**
   * Names of currently registered tools mapped to the `projectPath`-strip mode used when they were
   * registered (i.e. whether [projectPathParameterName] was stripped from their input schema).
   * Guarded by [updateMutex].
   */
  private val registeredToolsStrippedMode = mutableMapOf<String, Boolean>()

  /**
   * Last [updateTools] inputs that were successfully applied. Lets repeated calls with an unchanged
   * tools list reference and unchanged `projectKnownUpfront` mode short-circuit before computing or
   * applying a diff. This covers the StateFlow's initial replay right after the explicit pre-init
   * call from [McpServerService], the `clientInfo`-update echo through the tools flow, and repeated
   * roots notifications that report the same set — for the whole session lifetime, not just the
   * first emission. Guarded by [updateMutex].
   */
  private var lastAppliedToolsState: AppliedToolsState? = null

  fun updateClientInfo(newClientInfo: Implementation) {
    toolsProvider.updateClientInfo(newClientInfo)
  }

  internal suspend fun updateTools() {
    updateTools(mcpTools.value)
  }

  private suspend fun updateTools(newTools: List<McpTool>): Unit = updateMutex.withLock {
    val resolvedProject = try {
      resolveSessionProject()
    }
    catch (t: Throwable) {
      logger.trace { "Tools update could not resolve a target project: ${t.message}" }
      null
    }
    resolvedSessionProject.set(resolvedProject)
    val projectKnownUpfront = !projectPathFromInitialRequest.isNullOrBlank() || resolvedProject != null

    val newState = AppliedToolsState(tools = newTools, projectKnownUpfront = projectKnownUpfront)
    if (lastAppliedToolsState?.matches(newState) == true) {
      return@withLock
    }

    val desiredNames = newTools.mapTo(mutableSetOf()) { it.descriptor.name }

    val toolsToRemove = registeredToolsStrippedMode.entries
      .filter { (name, stripped) -> name !in desiredNames || stripped != projectKnownUpfront }
      .map { it.key }
    if (toolsToRemove.isNotEmpty()) {
      logger.trace { "Removing tools from MCP server: $toolsToRemove" }
      mcpServer.removeTools(toolsToRemove)
      toolsToRemove.forEach { registeredToolsStrippedMode.remove(it) }
    }

    val toolsToAdd = newTools.filter { it.descriptor.name !in registeredToolsStrippedMode }
    if (toolsToAdd.isNotEmpty()) {
      logger.trace { "Adding tools to MCP server: ${toolsToAdd.map { it.descriptor.name }}" }
      mcpServer.addTools(toolsToAdd.map { mcpToolToRegisteredTool(it, projectKnownUpfront) })
      toolsToAdd.forEach { registeredToolsStrippedMode[it.descriptor.name] = projectKnownUpfront }
    }

    lastAppliedToolsState = newState
  }

  /**
   * Creates and configures a new session with the given transport.
   * Sets up onClose handler, onInitialized handler and launches the tool updates collector.
   */
  suspend fun createAndInitializeSession(transport: Transport): ServerSession {
    val session = mcpServer.createSession(transport)
    sessionAwaiter.complete(session)
    val sessionId = session.sessionId

    transport.onClose {
      sessionScope.cancel()
      service<McpDiagnosticService>().sessionEnded(sessionId)
    }

    sessionScope.launch {
      logger.trace { "Subscribing to MCP tools updates for session ${sessionId}" }
      mcpTools.collectLatest { updatedTools ->
        updateTools(updatedTools)
      }
    }

    session.onInitialized {
      logger.trace {
        "Session initialized: sessionId=${session.sessionId}, clientVersion=${session.clientVersion?.name}, " +
        "localAgentId=${sessionOptions.localAgentId}"
      }
      // Update clientInfo when session is initialized
      val clientVersion = session.clientVersion
      if (clientVersion != null) {
        // Update session tools manager with client info
        updateClientInfo(clientVersion)
        service<McpDiagnosticService>().sessionStarted(
          sessionId = sessionId,
          clientInfo = ClientInfo(clientVersion.name, clientVersion.version),
          transportType = transportType,
          startTimeMs = System.currentTimeMillis(),
          localAgentId = sessionOptions.localAgentId,
          toolsCount = mcpTools.value.size,
        )
      }

      val clientCapabilities = session.clientCapabilities
      if (clientCapabilities?.roots != null) {
        session.onClose {
          logger.trace {
            "Roots for session ${session.sessionId} cleared"
          }
          sessionRoots.set(null)
          resolvedSessionProject.set(null)
        }
        session.setNotificationHandler<RootsListChangedNotification>(Method.Defined.NotificationsRootsListChanged) {
          sessionScope.async {
            val roots = session.roots()
            logger.trace {
              "Received roots list changed notification for session ${session.sessionId}: $roots roots"
            }
            setSessionRoots(roots)
          }
        }
        sessionScope.launch {
          val roots = session.roots()
          logger.trace {
            "Initialized roots for session ${session.sessionId}: $roots roots"
          }
          setSessionRoots(roots)
        }
      }

      // Log available tools via OpenTelemetry
      val span = getTracer().spanBuilder("mcp.session.initialized", TracerLevel.DEFAULT)
        .setAllAttributes(
          Attributes.builder()
            .put("mcp.session.id", session.sessionId)
            .put("mcp.client.name", clientVersion?.name ?: "unknown")
            .put("mcp.client.version", clientVersion?.version ?: "unknown")
            .put("mcp.tools.count", mcpTools.value.size.toLong())
            .put("mcp.tools.list", mcpTools.value.joinToString(", ") { it.descriptor.name })
            .build()
        )
        .startSpan()
      span.end()
    }

    return session
  }

  private suspend fun setSessionRoots(roots: Set<String>) {
    sessionRoots.set(roots)
    updateTools()
  }

  /**
   * Throws if no suitable project can be determined; callers decide how to handle that.
   */
  @Throws(McpExpectedError::class)
  private suspend fun resolveSessionProject(
    projectPathFromArgument: String? = null,
    projectPathFromCallHeader: String? = null,
  ): Project {
    return service<McpSessionProjectResolver>().resolveSessionProject(
      projectPathFromArgument = projectPathFromArgument,
      projectPathFromCallHeader = projectPathFromCallHeader,
      projectPathFromSessionHeader = projectPathFromInitialRequest,
      roots = sessionRoots.get() ?: emptySet(),
    )
  }

  private fun mcpToolToRegisteredTool(mcpTool: McpTool, projectKnownUpfront: Boolean): RegisteredTool {
    val tool = mcpTool.toSdkTool(stripPropertyName = if (projectKnownUpfront) projectPathParameterName else null)
    return RegisteredTool(tool) { request ->
      val session = sessionAwaiter.await()
      val httpRequest = currentCoroutineContext().httpRequestOrNull

      val projectPathFromMcpRequest = (request.arguments?.get(projectPathParameterName) as? JsonPrimitive)?.content
      val projectPathFromCallHeader =
        httpRequest?.headers?.get(IJ_MCP_SERVER_PROJECT_PATH)
        ?: (request.meta?.get(IJ_MCP_SERVER_PROJECT_PATH) as? JsonPrimitive)?.content

      val project = try {
        resolveSessionProject(
          projectPathFromArgument = projectPathFromMcpRequest,
          projectPathFromCallHeader = projectPathFromCallHeader,
        )
      }
      catch (tce: TimeoutCancellationException) {
        logger.trace { "Calling of tool '${mcpTool.descriptor.name}' has been timed out: ${tce.message}" }
        return@RegisteredTool McpToolCallResult.error(errorMessage = "Calling of tool '${mcpTool.descriptor.name}' has been timed out: ${tce.message}")
          .toSdkToolCallResult()
      }
      // handle it here because it incorrectly handled in the MCP SDK
      catch (@Suppress("IncorrectCancellationExceptionHandling") ce: CancellationException) {
        //logger.trace { "Calling of tool '${descriptor.name}' has been cancelled: ${ce.message}" }
        return@RegisteredTool McpToolCallResult.error(errorMessage = "Calling of tool '${mcpTool.descriptor.name}' has been cancelled: ${ce.message}")
          .toSdkToolCallResult()
      }
      catch (mcpError: McpExpectedError) {
        return@RegisteredTool McpToolCallResult.error(
          errorMessage = mcpError.mcpErrorText,
          structuredContent = mcpError.mcpErrorStructureContent
        ).toSdkToolCallResult()
      }
      catch (e: Throwable) {
        logger.error("Failed to determine project for MCP tool call by provided arguments", e)
        return@RegisteredTool McpToolCallResult.error(errorMessage = e.message ?: "Unknown error", structuredContent = null)
          .toSdkToolCallResult()
      }

      val headersWithoutAuthToken = httpRequest?.headers?.toMap()?.let { it - IJ_MCP_AUTH_TOKEN }

      val clientVersion = session.clientVersion ?: Implementation("Unknown MCP client", "Unknown version")

      val additionalData = McpCallInfo(
        callId = McpServerService.callId.getAndAdd(1),
        clientInfo = ClientInfo(clientVersion.name, clientVersion.version),
        project = project,
        mcpToolDescriptor = mcpTool.descriptor,
        rawArguments = request.arguments ?: EmptyJsonObject,
        meta = request.meta?.json ?: EmptyJsonObject,
        mcpSessionOptions = sessionOptions,
        headers = headersWithoutAuthToken ?: emptyMap(),
        sessionId = session.sessionId,
      ).apply {
        sessionHandler = this@McpSessionHandler
      }

      val callResult = withContext(McpCallAdditionalDataElement(additionalData) + McpSessionElement(session, elicitationKind)) {
        val toolExecution: suspend CoroutineScope.() -> McpToolCallResult = toolExecution@{
          val span = getTracer().spanBuilder("mcp.tool.call", TracerLevel.DEFAULT)
            .setAllAttributes(
              Attributes.builder()
                .put("mcp.tool.name", mcpTool.descriptor.name)
                .put("mcp.client.name", clientVersion.name)
                .put("mcp.client.version", clientVersion.version)
                .put("mcp.call.id", additionalData.callId.toLong())
                .put("mcp.session.id", session.sessionId)
                .build()
            )
            .startSpan()

          try {
            span.makeCurrent().use {
              @Suppress("IncorrectCancellationExceptionHandling")
              try {
                application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                  .beforeMcpToolCall(mcpTool.descriptor, additionalData)

                logger.trace { "Start calling tool '${mcpTool.descriptor.name}'. Arguments: ${request.arguments}" }

                span.addEvent(
                  "mcp.tool.call.started",
                  Attributes.of(
                    AttributeKey.stringKey("arguments.size"),
                    request.arguments?.size?.toString() ?: "0"
                  )
                )

                val sideEffectResult = processSideEffects(additionalData.callId) {
                  mcpTool.call(request.arguments ?: EmptyJsonObject)
                }

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
                  .afterMcpToolCall(mcpTool.descriptor, sideEffectResult.events, null, additionalData, sideEffectResult.result.deepCopy())
                sideEffectResult.result
              }
              catch (ce: CancellationException) {
                val message = "MCP tool call has been cancelled likely by a user interaction: ${ce.message}"
                logger.traceThrowable { CancellationException(message, ce) }
                span.setStatus(StatusCode.ERROR, message)
                application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                  .afterMcpToolCall(mcpTool.descriptor, emptyList(), ce, additionalData)
                McpToolCallResult.error(message)
              }
              catch (mcpException: McpExpectedError) {
                logger.traceThrowable { mcpException }
                span.setStatus(StatusCode.ERROR, "MCP expected error: ${mcpException.mcpErrorText}")
                application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                  .afterMcpToolCall(mcpTool.descriptor, emptyList(), mcpException, additionalData)
                McpToolCallResult.error(mcpException.mcpErrorText, mcpException.mcpErrorStructureContent)
              }
              catch (t: Throwable) {
                val errorMessage = "MCP tool call has been failed: ${t.message}"
                logger.error(t)
                span.setStatus(StatusCode.ERROR, errorMessage)
                application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                  .afterMcpToolCall(mcpTool.descriptor, emptyList(), t, additionalData)
                McpToolCallResult.error(errorMessage)
              }
              finally {
                McpServerCounterUsagesCollector.logMcpToolCall(mcpTool.descriptor)
              }
            }
          }
          finally {
            span.end()
          }
        }

        session.callToolWithProgressNotifications(project, request.meta?.progressToken, toolExecution)
      }

      val callToolResult = callResult.toSdkToolCallResult()
      return@RegisteredTool callToolResult
    }
  }

  private data class AppliedToolsState(
    val tools: List<McpTool>,
    val projectKnownUpfront: Boolean,
  ) {
    fun matches(other: AppliedToolsState): Boolean =
      tools === other.tools && projectKnownUpfront == other.projectKnownUpfront
  }
}

private suspend fun ServerSession.roots(): Set<String> {
  return listRoots().roots.map { it.uri }.toSet()
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

private fun McpToolCallResult.toSdkToolCallResult(): CallToolResult {
  val contents = content.map { content ->
    when (content) {
      is McpToolCallResultContent.Text -> TextContent(content.text)
    }
  }
  val structuredContent = if (structuredToolOutputEnabled && !isError) structuredContent else null
  val callToolResult = CallToolResult(content = contents, structuredContent = structuredContent, isError = isError)
  return callToolResult
}

private fun McpTool.toSdkTool(stripPropertyName: String? = null): Tool {
  val outputSchema = if (structuredToolOutputEnabled) {
    descriptor.outputSchema?.let {
      ToolSchema(
        properties = it.propertiesSchema,
        required = it.requiredProperties.toList())
    }
  }
  else null

  val inputProperties = descriptor.inputSchema.propertiesSchema
  val inputRequired = descriptor.inputSchema.requiredProperties

  val effectiveProperties: JsonObject
  val effectiveRequired: List<String>
  if (stripPropertyName != null && inputProperties.containsKey(stripPropertyName)) {
    effectiveProperties = JsonObject(inputProperties.filterKeys { it != stripPropertyName })
    effectiveRequired = inputRequired.filter { it != stripPropertyName }
  }
  else {
    effectiveProperties = inputProperties
    effectiveRequired = inputRequired.toList()
  }

  val tool = Tool(
    name = descriptor.name,
    title = descriptor.title,
    description = descriptor.description,
    inputSchema = ToolSchema(
      properties = effectiveProperties,
      required = effectiveRequired,
    ),
    outputSchema = outputSchema,
    annotations = descriptor.annotations,
  )
  return tool
}
