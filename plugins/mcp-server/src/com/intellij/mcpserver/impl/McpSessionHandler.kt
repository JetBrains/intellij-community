package com.intellij.mcpserver.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.mcpserver.ClientInfo
import com.intellij.mcpserver.DirectoryCreatedEvent
import com.intellij.mcpserver.DirectoryDeletedEvent
import com.intellij.mcpserver.FileContentChangeEvent
import com.intellij.mcpserver.FileCreatedEvent
import com.intellij.mcpserver.FileDeletedEvent
import com.intellij.mcpserver.FileMovedEvent
import com.intellij.mcpserver.McpCallAdditionalDataElement
import com.intellij.mcpserver.McpCallInfo
import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCallResultContent
import com.intellij.mcpserver.McpToolSideEffectEvent
import com.intellij.mcpserver.ToolCallListener
import com.intellij.mcpserver.impl.util.network.httpRequestOrNull
import com.intellij.mcpserver.impl.util.projectPathParameterName
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.mcpCallInfoOrNull
import com.intellij.mcpserver.settings.McpToolFilterSettings
import com.intellij.mcpserver.noSuitableProjectError
import com.intellij.mcpserver.statistics.McpServerCounterUsagesCollector
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.mcpserver.util.findMostRelevantProject
import com.intellij.mcpserver.util.findMostRelevantProjectForRoots
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.diagnostic.traceThrowable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.platform.diagnostic.telemetry.IJNoopTracer
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.application
import com.intellij.util.asDisposable
import io.ktor.server.application.Application
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.iterator
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
    private val projectPathFromInitialRequest: String?,
    useFiltersFromEP: Boolean,
) {
  private val sessionScope = parentScope.childScope("SessionMcpToolsManager")

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
  val routerToolsProvider: McpFilteredToolsListProvider? =
    if (invocationMode == McpSessionInvocationMode.VIA_ROUTER)
      McpFilteredToolsListProvider(
        sessionScope,
        sessionOptions,
        mcpServerService,
        useFiltersFromEP,
        invocationMode = McpToolInvocationMode.VIA_ROUTER
      )
    else
      null

  val mcpTools = toolsProvider.mcpTools

  private var previousTools: List<McpTool>? = null

  private val sessionAwaiter = CompletableDeferred<ServerSession>()
  private val sessionRoots = AtomicReference<Set<String>?>(null)

  init {
    // Process initial tools immediately to fix race condition
    processToolsUpdate(mcpTools.value)
  }

  fun updateClientInfo(newClientInfo: Implementation) {
    toolsProvider.updateClientInfo(newClientInfo)
  }

  /**
   * Processes tool updates by applying filters and updating MCP server tools.
   * This method extracts the logic from collectLatest handler.
   */
  private fun processToolsUpdate(updatedTools: List<McpTool>) {
    val previousToolNames = previousTools?.map { it.descriptor.name }?.toSet() ?: emptySet()
    val newToolNames = updatedTools.map { it.descriptor.name }.toSet()

    // Find tools to remove (in previous but not in new)
    val toolsToRemove = previousToolNames - newToolNames
    if (toolsToRemove.isNotEmpty()) {
      logger.trace { "Removing tools from MCP server: $toolsToRemove" }
      mcpServer.removeTools(toolsToRemove.toList())
    }

    // Find tools to add (in new but not in previous)
    val toolNamesToAdd = newToolNames - previousToolNames
    val toolsToAdd = updatedTools.filter { it.descriptor.name in toolNamesToAdd }
    if (toolsToAdd.isNotEmpty()) {
      logger.trace { "Adding tools to MCP server: ${toolsToAdd.map { it.descriptor.name }}" }
      mcpServer.addTools(toolsToAdd.map { mcpToolToRegisteredTool(it) })
    }

    previousTools = updatedTools
  }

  /**
   * Creates and configures a new session with the given transport.
   * Sets up onClose handler, onInitialized handler and launches the tool updates collector.
   */
  suspend fun createAndInitializeSession(transport: Transport, app: Application): ServerSession {
    val session = mcpServer.createSession(transport)
    sessionAwaiter.complete(session)

    session.onClose {
      sessionScope.cancel()
    }

    app.launch {
      logger.trace { "Subscribing to MCP tools updates for session ${session.sessionId}" }
      mcpTools.collectLatest { updatedTools ->
        processToolsUpdate(updatedTools)
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
      }

      val clientCapabilities = session.clientCapabilities
      if (clientCapabilities?.roots != null) {
        session.onClose {
          logger.trace {
            "Roots for session ${session.sessionId} cleared"
          }
          sessionRoots.set(null)
        }
        session.setNotificationHandler<RootsListChangedNotification>(Method.Defined.NotificationsRootsListChanged) {
          app.async {
            val roots = session.roots()
            logger.trace {
              "Received roots list changed notification for session ${session.sessionId}: $roots roots"
            }
            sessionRoots.set(roots)
          }
        }
        app.launch {
          val roots = session.roots()
          logger.trace {
            "Initialized roots for session ${session.sessionId}: $roots roots"
          }
          sessionRoots.set(roots)
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

  private fun mcpToolToRegisteredTool(mcpTool: McpTool): RegisteredTool {
    val tool = mcpTool.toSdkTool()
    return RegisteredTool(tool) { request ->
        val session = sessionAwaiter.await()
        val httpRequest = currentCoroutineContext().httpRequestOrNull
        val projectPathFromHeaders =
            httpRequest?.headers?.get(IJ_MCP_SERVER_PROJECT_PATH)
                ?: (request.meta?.get(IJ_MCP_SERVER_PROJECT_PATH) as? JsonPrimitive)?.content
                ?: projectPathFromInitialRequest
        val projectPathFromMcpRequest = (request.arguments?.get(projectPathParameterName) as? JsonPrimitive)?.content
        val project = try {
            val roots = sessionRoots.get()
            val projectFromRootList = roots?.let { findMostRelevantProjectForRoots(roots) }
            logger.trace { "Locating project for session: ${session.sessionId}, roots: $roots, ${projectPathParameterName}: $projectPathFromMcpRequest, projectPathFromHeaders: $projectPathFromHeaders" }
            if (projectFromRootList != null) {
                logger.trace { "Project $projectFromRootList from roots list: $roots" }
                // prefer project from list of roots
                projectFromRootList
            } else if (!projectPathFromMcpRequest.isNullOrBlank()) {
                logger.trace { "Project path specified in MCP request: $projectPathFromMcpRequest" }
                // project from mcp argument first (may hallucinate)
                findMostRelevantProject(projectPathFromMcpRequest)
                    ?: throw noSuitableProjectError("`${projectPathParameterName}`=`$projectPathFromMcpRequest` doesn't correspond to any open project.")
            } else if (!projectPathFromHeaders.isNullOrBlank()) {
                logger.trace { "Project path specified in MCP request headers: $projectPathFromHeaders" }
                // then from headers
                findMostRelevantProject(projectPathFromHeaders)
                    ?: throw noSuitableProjectError("Project path specified via header variable `${IJ_MCP_SERVER_PROJECT_PATH}`=`$projectPathFromHeaders` doesn't correspond to any open project.")
            } else {
                null
            }
        } catch (tce: TimeoutCancellationException) {
            logger.trace { "Calling of tool '${mcpTool.descriptor.name}' has been timed out: ${tce.message}" }
            return@RegisteredTool McpToolCallResult.error(errorMessage = "Calling of tool '${mcpTool.descriptor.name}' has been timed out: ${tce.message}")
                .toSdkToolCallResult()
        }
        // handle it here because it incorrectly handled in the MCP SDK
        catch (@Suppress("IncorrectCancellationExceptionHandling") ce: CancellationException) {
            //logger.trace { "Calling of tool '${descriptor.name}' has been cancelled: ${ce.message}" }
            return@RegisteredTool McpToolCallResult.error(errorMessage = "Calling of tool '${mcpTool.descriptor.name}' has been cancelled: ${ce.message}")
                .toSdkToolCallResult()
        } catch (mcpError: McpExpectedError) {
            return@RegisteredTool McpToolCallResult.error(
                errorMessage = mcpError.mcpErrorText,
                structuredContent = mcpError.mcpErrorStructureContent
            ).toSdkToolCallResult()
        } catch (e: Throwable) {
            logger.error("Failed to determine project for MCP tool call by provided arguments", e)
            return@RegisteredTool McpToolCallResult.error(errorMessage = e.message ?: "Unknown error", structuredContent = null)
                .toSdkToolCallResult()
        }

        val headersWithoutAuthToken = httpRequest?.headers?.toMap()?.let { it - IJ_MCP_AUTH_TOKEN }

        val vfsEvent = CopyOnWriteArrayList<VFileEvent>()
        val initialDocumentContents = ConcurrentHashMap<Document, String>()
        val clientVersion = session.clientVersion ?: Implementation("Unknown MCP client", "Unknown version")

        val sessionId = session.sessionId
        val additionalData = McpCallInfo(
            callId = McpServerService.callId.getAndAdd(1),
            clientInfo = ClientInfo(clientVersion.name, clientVersion.version),
            project = project,
            mcpToolDescriptor = mcpTool.descriptor,
            rawArguments = request.arguments ?: EmptyJsonObject,
            meta = request.meta?.json ?: EmptyJsonObject,
            mcpSessionOptions = sessionOptions,
            headers = headersWithoutAuthToken ?: emptyMap(),
        ).apply {
            sessionHandler = this@McpSessionHandler
        }

        val callResult = coroutineScope {

            VirtualFileManager.getInstance().addAsyncFileListener(this, AsyncFileListener { events ->
                val inHandlerInfo = currentThreadContext().mcpCallInfoOrNull
                if (inHandlerInfo != null && inHandlerInfo.callId == additionalData.callId) {
                    logger.trace { "VFS changes detected for call: $inHandlerInfo" }
                    vfsEvent.addAll(events)
                }
                // probably we have to read initial contents here
                // see comment below near `is VFileContentChangeEvent`
                return@AsyncFileListener object : AsyncFileListener.ChangeApplier {}
            })

            val documentListener = object : DocumentListener {
                // record content before any change
                override fun beforeDocumentChange(event: DocumentEvent) {
                    val inHandlerInfo = currentThreadContext().mcpCallInfoOrNull
                    if (inHandlerInfo != null && inHandlerInfo.callId == additionalData.callId) {
                        logger.trace { "Document changes detected for call: $inHandlerInfo" }
                        initialDocumentContents.computeIfAbsent(event.document) { event.document.text }
                    }
                }
            }

            EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this.asDisposable())

            withContext(
                McpCallAdditionalDataElement(additionalData)
            ) {
                val span = getTracer().spanBuilder("mcp.tool.call", TracerLevel.DEFAULT)
                    .setAllAttributes(
                        Attributes.builder()
                            .put("mcp.tool.name", mcpTool.descriptor.name)
                            .put("mcp.client.name", clientVersion.name)
                            .put("mcp.client.version", clientVersion.version)
                            .put("mcp.call.id", additionalData.callId.toLong())
                            .put("mcp.session.id", sessionId)
                            .build()
                    )
                    .startSpan()

                try {
                    span.makeCurrent().use {
                        val sideEffectEvents = mutableListOf<McpToolSideEffectEvent>()
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

                            val result = mcpTool.call(request.arguments ?: EmptyJsonObject)

                            logger.trace {
                                "Tool call successful '${mcpTool.descriptor.name}'. Result: ${
                                    result.content.joinToString("\n") { it.toString() }
                                }"
                            }

                            span.addEvent(
                                "mcp.tool.call.completed",
                                Attributes.of(
                                    AttributeKey.stringKey("result.content.count"),
                                    result.content.size.toString()
                                )
                            )
                            span.setStatus(StatusCode.OK)

                            try {
                                val processedChangedFiles = mutableSetOf<VirtualFile>()

                                for ((doc, oldContent) in initialDocumentContents) {
                                    val virtualFile = FileDocumentManager.getInstance().getFile(doc) ?: continue
                                    val newContent = readAction { doc.text }
                                    sideEffectEvents.add(FileContentChangeEvent(virtualFile, oldContent, newContent))
                                    processedChangedFiles.add(virtualFile)
                                }

                                for (event in vfsEvent) {
                                    when (event) {
                                        is VFileMoveEvent -> {
                                            sideEffectEvents.add(FileMovedEvent(event.file, event.oldParent, event.newParent))
                                        }

                                        is VFileCreateEvent -> {
                                            val virtualFile = event.file ?: continue
                                            if (event.isDirectory) {
                                                sideEffectEvents.add(DirectoryCreatedEvent(virtualFile))
                                            } else {
                                                val newContent =
                                                    readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text }
                                                        ?: continue
                                                sideEffectEvents.add(FileCreatedEvent(virtualFile, newContent))
                                            }
                                        }

                                        is VFileDeleteEvent -> {
                                            val virtualFile = event.file
                                            if (virtualFile.isDirectory) {
                                                sideEffectEvents.add(DirectoryDeletedEvent(virtualFile))
                                            } else {
                                                val document =
                                                    readAction { FileDocumentManager.getInstance().getDocument(virtualFile) } ?: continue
                                                val oldContent = initialDocumentContents[document]
                                                sideEffectEvents.add(FileDeletedEvent(virtualFile, oldContent))
                                            }
                                        }

                                        is VFileCopyEvent -> {
                                            val createdFile = event.findCreatedFile() ?: continue
                                            val newContent =
                                                readAction { FileDocumentManager.getInstance().getDocument(createdFile)?.text } ?: continue
                                            sideEffectEvents.add(FileCreatedEvent(createdFile, newContent))
                                        }

                                        is VFileContentChangeEvent -> {
                                            // reported in documents loop
                                            if (processedChangedFiles.contains(event.file)) continue
                                            val virtualFile = event.file
                                            val newContent =
                                                readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text } ?: continue
                                            // Important: there may be a case when file is changed via low-level change (like File.replaceText).
                                            // in this case we don't track the old content, because it may be heavy, it requires loading the file in
                                            // AsyncFileListener above and decoding with encoding etc. The file can be binary etc.
                                            sideEffectEvents.add(
                                                FileContentChangeEvent(
                                                    virtualFile,
                                                    oldContent = null,
                                                    newContent = newContent
                                                )
                                            )
                                        }
                                    }
                                }

                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (t: Throwable) {
                                logger.error(
                                    "Failed to process changed documents after calling MCP tool ${mcpTool.descriptor.name}",
                                    t
                                )
                            }
                            application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                                .afterMcpToolCall(mcpTool.descriptor, sideEffectEvents, null, additionalData)
                            result
                        } catch (ce: CancellationException) {
                            val message = "MCP tool call has been cancelled likely by a user interaction: ${ce.message}"
                            logger.traceThrowable { CancellationException(message, ce) }
                            span.setStatus(StatusCode.ERROR, message)
                            application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                                .afterMcpToolCall(mcpTool.descriptor, sideEffectEvents, ce, additionalData)
                            McpToolCallResult.error(message)
                        } catch (mcpException: McpExpectedError) {
                            logger.traceThrowable { mcpException }
                            span.setStatus(StatusCode.ERROR, "MCP expected error: ${mcpException.mcpErrorText}")
                            application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                                .afterMcpToolCall(mcpTool.descriptor, sideEffectEvents, mcpException, additionalData)
                            McpToolCallResult.error(mcpException.mcpErrorText, mcpException.mcpErrorStructureContent)
                        } catch (t: Throwable) {
                            val errorMessage = "MCP tool call has been failed: ${t.message}"
                            logger.error(t)
                            span.setStatus(StatusCode.ERROR, errorMessage)
                            application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                                .afterMcpToolCall(mcpTool.descriptor, sideEffectEvents, t, additionalData)
                            McpToolCallResult.error(errorMessage)
                        } finally {
                            if (sideEffectEvents.isNotEmpty()) {
                                withContext(Dispatchers.EDT) {
                                    writeIntentReadAction {
                                        FileDocumentManager.getInstance().saveAllDocuments()
                                    }
                                }
                            }
                            span.setAllAttributes(
                                Attributes.builder()
                                    .put("mcp.side_effects.vfs_events", vfsEvent.size.toLong())
                                    .put("mcp.side_effects.document_changes", initialDocumentContents.size.toLong())
                                    .build()
                            )
                            McpServerCounterUsagesCollector.reportMcpCall(mcpTool.descriptor)
                        }
                    }
                } finally {
                    span.end()
                }
            }
        }

        val callToolResult = callResult.toSdkToolCallResult()
        return@RegisteredTool callToolResult
    }
  }
}

private suspend fun ServerSession.roots(): Set<String> {
  return listRoots().roots.map { it.uri }.toSet()
}

private fun McpToolCallResult.toSdkToolCallResult(): CallToolResult {
  val contents = content.map { content ->
    when (content) {
      is McpToolCallResultContent.Text -> TextContent(content.text)
    }
  }
  val structuredContent = if (structuredToolOutputEnabled) structuredContent else null
  val callToolResult = CallToolResult(content = contents, structuredContent = structuredContent, isError = isError)
  return callToolResult
}

private fun McpTool.toSdkTool(): Tool {
  val outputSchema = if (structuredToolOutputEnabled) {
    descriptor.outputSchema?.let {
      ToolSchema(
        properties = it.propertiesSchema,
        required = it.requiredProperties.toList())
    }
  }
  else null
  val tool = Tool(name = descriptor.name,
                  title = null,
                  description = descriptor.description,
                  inputSchema = ToolSchema(
                    properties = descriptor.inputSchema.propertiesSchema,
                    required = descriptor.inputSchema.requiredProperties.toList()),
                  outputSchema = outputSchema,
                  annotations = null)
  return tool
}
