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
import com.intellij.mcpserver.McpToolFilter
import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolSideEffectEvent
import com.intellij.mcpserver.McpToolsProvider
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.ToolCallListener
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.impl.util.network.findFirstFreePort
import com.intellij.mcpserver.impl.util.network.httpRequestOrNull
import com.intellij.mcpserver.impl.util.network.installHostValidation
import com.intellij.mcpserver.impl.util.network.installHttpRequestPropagation
import com.intellij.mcpserver.impl.util.network.mcpPatched
import com.intellij.mcpserver.impl.util.projectPathParameterName
import com.intellij.mcpserver.mcpCallInfoOrNull
import com.intellij.mcpserver.noSuitableProjectError
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.statistics.McpServerCounterUsagesCollector
import com.intellij.mcpserver.stdio.IJ_MCP_ALLOWED_TOOLS
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.mcpserver.util.findMostRelevantProject
import com.intellij.mcpserver.util.findMostRelevantProjectForRoots
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.diagnostic.traceThrowable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
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
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.diagnostic.telemetry.IJNoopTracer
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import com.intellij.util.application
import com.intellij.util.asDisposable
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.util.toMap
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.RootsListChangedNotification
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException


private val logger = logger<McpServerService>()
private val structuredToolOutputEnabled get() = Registry.`is`("mcp.server.structured.tool.output")
private val IJ_MCP_AUTH_TOKEN: String = ::IJ_MCP_AUTH_TOKEN.name
private val MCP_SERVER_TRACER_SCOPE = Scope("mcpServer")

private fun getTracer(): IJTracer =
  if (Registry.`is`("mcp.server.ot.trace"))
    TelemetryManager.getInstance().getTracer(MCP_SERVER_TRACER_SCOPE)
  else
    IJNoopTracer

@Service(Service.Level.APP)
class McpServerService(val cs: CoroutineScope) {
  enum class AskCommandExecutionMode {
    ASK,
    DONT_ASK,

    /**
     * Respects brave mode flag
     */
    RESPECT_GLOBAL_SETTINGS,
  }
  class McpSessionOptions(
    val commandExecutionMode: AskCommandExecutionMode,
    val toolFilter: McpToolFilter = McpToolFilter.AllowAll,
    val localAgentId: String? = null,
  )

  companion object {
    fun getInstance(): McpServerService = service()
    suspend fun getInstanceAsync(): McpServerService = serviceAsync()
  }

  private val server = MutableStateFlow(startGlobalServerIfEnabled())

  private class ServerAndCount(var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>?, var userCount: Int)

  private val privateServer: ServerAndCount = ServerAndCount(null, 0)
  private val privateServerMutex = Mutex()
  private val callId = AtomicInteger(0)

  private val activeAuthorizedSessions = ConcurrentHashMap<String, McpSessionOptions>()
  internal val sessionRoots = ConcurrentHashMap<String, Set<String>>()

  val isRunning: Boolean
    get() = server.value != null

  // For tests
  val theOnlySession: McpSessionOptions?
    @ApiStatus.Internal
    get() = if (activeAuthorizedSessions.size == 1) activeAuthorizedSessions.values.firstOrNull() else null

  private val connectionAddressProvider: McpServerConnectionAddressProvider
    get() = service()

  val serverSseUrl: String
    get() = connectionAddressProvider.serverSseUrl

  val serverStreamUrl: String
    get() = connectionAddressProvider.serverStreamUrl

  fun start() {
    McpServerSettings.getInstance().state.enableMcpServer = true
    settingsChanged(true)
  }

  fun stop() {
    McpServerSettings.getInstance().state.enableMcpServer = false
    settingsChanged(false)
  }

  // probably we have to add an ability to configure server before start (like tools list, features, etc.)
  /**
   * Starts an isolated MCP on a separate port that doesn't interfere with the main MCP server,
   * thus it can be used even if the main MCP server is enabled by a user.
   * This isolated server runs only for the method execution period.
   * The server is secured by a temporary token IJ_MCP_AUTH_TOKEN that is generated for each session.
   * The calling site should pass the token value in http headers.
   * @param block suspend function that runs in the isolated MCP server context
   */
  suspend fun authorizedSession(
    mcpSessionOptions: McpSessionOptions,
    block: suspend CoroutineScope.(port: Int, authTokenName: String, authTokenValue: String) -> Unit,
  ) {
    // open server here on random port
    val uuid = UUID.randomUUID().toString()

    val server = privateServerMutex.withLock {
      if (privateServer.server == null) {
        logger.trace { "No active private server. Starting private MCP server..." }
        privateServer.server = startServer(desiredPort = McpServerSettings.DEFAULT_MCP_PRIVATE_PORT, authCheck = true)
      }
      privateServer.userCount++
      logger.trace { "Current private server user count before session $uuid: ${privateServer.userCount}" }
      return@withLock privateServer.server ?: error("Server must not be null")
    }
    try {
      val occupiedPort = server.engine.resolvedConnectors().first().port
      logger.trace { "Authorized MCP session started on port $occupiedPort" }
      activeAuthorizedSessions[uuid] = mcpSessionOptions
      coroutineScope {
        block(occupiedPort, IJ_MCP_AUTH_TOKEN, uuid)
      }
    }
    finally {
      activeAuthorizedSessions.remove(uuid)
      privateServerMutex.withLock {
        privateServer.userCount--
        logger.trace { "Current private server user count after session $uuid: ${privateServer.userCount}" }
        if (privateServer.userCount == 0) {
          logger.trace { "No active private server users. Stopping private MCP server..." }
          try {
            // if to call `stopSuspend` without NonCancellable in the case of the current coroutine cancellation the stopSuspend won't run
            // DO NOT merge `withContext(NonCancellable)` and `withContext(Dispatchers.IO)`, otherwise it throws cancellation
            withContext(NonCancellable) {
              withContext(Dispatchers.IO) {
                // timeout exception will be reported in the catch below
                withTimeout(2000) {
                  server.stopSuspend(gracePeriodMillis = 500, timeoutMillis = 1000)
                }
              }
            }
          }
          catch (t: Throwable) {
            logger.error("Failed to gracefully shutdown authorized MCP server", t)
          }
          privateServer.server = null
          logger.trace { "Private MCP server stopped" }
        }
      }
      logger.trace { "Authorized MCP session stopped" }
    }
  }

  private fun isKnownToken(token: String): Boolean {
    return activeAuthorizedSessions.containsKey(token)
  }

  private fun getSessionOptions(token: String?): McpSessionOptions {
    return token?.let { activeAuthorizedSessions[token] }
           ?: McpSessionOptions(commandExecutionMode = AskCommandExecutionMode.RESPECT_GLOBAL_SETTINGS)
  }

  val port: Int
    get() = (server.value ?: error("MCP Server is not enabled")).engineConfig.connectors.first().port

  internal fun resolvedConnectorHost(): String? {
    val currentServer = server.value ?: return null
    return currentServer.engineConfig.connectors.firstOrNull()?.host?.takeUnless { it.isBlank() }
  }

  internal fun settingsChanged(enabled: Boolean) {
    server.update { currentServer ->
      if (!enabled) {
        // stop old
        currentServer?.stop()
        return@update null
      }
      else {
        // reuse old or start new
        return@update currentServer ?: startServer(McpServerSettings.getInstance().state.mcpServerPort, authCheck = false)
      }
    }
  }

  class MyProjectListener : ProjectActivity {
    override suspend fun execute(project: Project) {
      // TODO: consider start on app startup
      serviceAsync<McpServerService>() // initialize service
    }
  }

  private fun startGlobalServerIfEnabled(): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? {
    if (!McpServerSettings.getInstance().state.enableMcpServer) return null
    val server = startServer(McpServerSettings.getInstance().state.mcpServerPort, authCheck = false)
    cs.launch {
      // save to settings can be done asynchronously
      McpServerSettings.getInstance().state.mcpServerPort = server.engine.resolvedConnectors().first().port
    }
    return server
  }

  private fun startServer(desiredPort: Int, authCheck: Boolean): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    val freePort = findFirstFreePort(desiredPort)

    val clientInfo = MutableStateFlow<Implementation?>(null)
    val currentSessionOptions = MutableStateFlow<McpSessionOptions?>(null)
    val mcpTools = MutableStateFlow(getMcpTools(clientInfo = null, sessionOptions = null))

    fun emitMcpTools(reason: String) {
      val currentClientInfo = clientInfo.value
      val currentOptions = currentSessionOptions.value
      logger.trace {
        "Emitting MCP tools update: reason=$reason, clientName=${currentClientInfo?.name}, " +
        "localAgentId=${currentOptions?.localAgentId}"
      }
      mcpTools.tryEmit(getMcpTools(clientInfo = currentClientInfo, sessionOptions = currentOptions))
    }

    McpToolsProvider.EP.addExtensionPointListener(cs, object : ExtensionPointListener<McpToolsProvider> {
      override fun extensionAdded(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) {
        emitMcpTools("McpToolsProvider extension added")
      }

      override fun extensionRemoved(extension: McpToolsProvider, pluginDescriptor: PluginDescriptor) {
        emitMcpTools("McpToolsProvider extension removed")
      }
    })

    McpToolset.EP.addExtensionPointListener(cs, object : ExtensionPointListener<McpToolset> {
      override fun extensionAdded(extension: McpToolset, pluginDescriptor: PluginDescriptor) {
        emitMcpTools("McpToolset extension added")
      }

      override fun extensionRemoved(extension: McpToolset, pluginDescriptor: PluginDescriptor) {
        emitMcpTools("McpToolset extension removed")
      }
    })

    val filterProvidersScope = AtomicReference<CoroutineScope?>(null)
    fun subscribeToFilterProviders(clientInfoValue: Implementation?, sessionOptionsValue: McpSessionOptions?) {
      filterProvidersScope.getAndSet(cs.childScope("subscribeToFilterProviders"))?.cancel()
      val currentScope = filterProvidersScope.get() ?: return
      McpToolFilterProvider.EP.extensionList.forEach { provider ->
        currentScope.launch {
          provider.getUpdates(clientInfoValue, currentScope, sessionOptionsValue).collectLatest {
            emitMcpTools("Filter provider update from ${provider.javaClass.simpleName}")
          }
        }
      }
    }

    McpToolFilterProvider.EP.addExtensionPointListener(cs, object : ExtensionPointListener<McpToolFilterProvider> {
      override fun extensionAdded(extension: McpToolFilterProvider, pluginDescriptor: PluginDescriptor) {
        subscribeToFilterProviders(clientInfo.value, currentSessionOptions.value)
        emitMcpTools("McpToolFilterProvider extension added")
      }

      override fun extensionRemoved(extension: McpToolFilterProvider, pluginDescriptor: PluginDescriptor) {
        subscribeToFilterProviders(clientInfo.value, currentSessionOptions.value)
        emitMcpTools("McpToolFilterProvider extension removed")
      }
    })

    return cs.embeddedServer(CIO, host = "127.0.0.1", port = freePort) {
      logger.trace { "Starting embedded MCP server on port $freePort, authCheck=$authCheck" }
      installHostValidation()
      installHttpRequestPropagation()

      mcpPatched(prePhase = {
        if (authCheck) {
          val authToken = call.request.headers[IJ_MCP_AUTH_TOKEN]
          if (authToken == null || !isKnownToken(authToken)) {
            call.respond(HttpStatusCode.Unauthorized, "MCP server is running in restricted mode. Please, provide valid authorization token")
            finish()
          }
        }
      }) { applicationCall, transport ->
        // this is added because now a Kotlin MCP client doesn't support header adjusting for each request, only for initial one, see McpStdioRunner
        val projectPath = applicationCall.request.headers[IJ_MCP_SERVER_PROJECT_PATH]
        val authToken = if (authCheck) applicationCall.request.headers[IJ_MCP_AUTH_TOKEN] else null

        // Check for tool filter from header (for stdio/CLI usage)
        val allowedToolsFromHeader = applicationCall.request.headers[IJ_MCP_ALLOWED_TOOLS]
        val headerFilter = allowedToolsFromHeader?.let { toolsStr ->
          val tools = toolsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
          if (tools.isNotEmpty()) McpToolFilter.AllowList(tools) else McpToolFilter.AllowAll
        }

        // Merge filters: auth-based session options take precedence over header
        val baseSessionOptions = getSessionOptions(authToken)
        // if no header provided, use the existing filter from sessionOptions
        val sessionOptions = if (headerFilter != null) {
          McpSessionOptions(baseSessionOptions.commandExecutionMode, headerFilter)
        } else {
          baseSessionOptions
        }
        val mcpServer = Server(
          Implementation(
            name = "${ApplicationNamesInfo.getInstance().fullProductName} MCP Server",
            version = ApplicationInfo.getInstance().fullVersion
          ),
          ServerOptions(
            capabilities = ServerCapabilities(
              //prompts = ServerCapabilities.Prompts(listChanged = true),
              //resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
              tools = ServerCapabilities.Tools(listChanged = true),
              logging = null,
              experimental = null,
              prompts = null,
              resources = null,
            )
          )
        )
        val session = mcpServer.createSession(transport)
        //session.setRequestHandler<LoggingMessageNotification.SetLevelRequest>(Method.Defined.LoggingSetLevel) { request, extra ->
        //  // Workaround inspector failure
        //  return@setRequestHandler EmptyRequestResult()
        //}
        launch {
          subscribeToFilterProviders(clientInfo.value, currentSessionOptions.value)

          var previousTools: List<McpTool>? = null
          mcpTools.collectLatest { updatedTools ->
            // Apply session-specific filter
            val filteredTools = updatedTools.filter { sessionOptions.toolFilter.shouldInclude(it.descriptor.name) }

            previousTools = updateMcpServerTools(
              mcpServer = mcpServer,
              session = session,
              projectPath = projectPath,
              previousTools = previousTools,
              newTools = filteredTools
            )
          }
        }

        session.onInitialized {
          logger.trace {
            "Session initialized: sessionId=${session.sessionId}, clientVersion=${session.clientVersion?.name}, " +
            "localAgentId=${sessionOptions.localAgentId}"
          }
          // Update clientInfo when session is initialized
          val clientVersion = session.clientVersion
          // Update sessionOptions when session is initialized
          currentSessionOptions.value = sessionOptions
          if (clientVersion != null) {
            clientInfo.value = clientVersion
            // Re-subscribe to filter providers with the new clientInfo and sessionOptions
            subscribeToFilterProviders(clientVersion, sessionOptions)
            // Re-fetch MCP tools with the new clientInfo and sessionOptions
            emitMcpTools("Session initialized with clientVersion=${clientVersion.name}")
          }

          val clientCapabilities = session.clientCapabilities
          if (clientCapabilities?.roots != null) {
            session.onClose {
              logger.trace {
                "Roots for session ${session.sessionId} cleared"
              }
              sessionRoots.remove(session.sessionId)
            }
            session.setNotificationHandler<RootsListChangedNotification>(Method.Defined.NotificationsRootsListChanged) {
              async {
                logger.trace {
                  "Received roots list changed notification for session ${session.sessionId}: ${session.roots()} roots"
                }
                sessionRoots[session.sessionId] = session.roots()
              }
            }
            launch {
              logger.trace {
                "Initialized roots for session ${session.sessionId}: ${session.roots()} roots"
              }
              sessionRoots[session.sessionId] = session.roots()
            }
          }
        }


        return@mcpPatched session
      }
    }.start(wait = false)
  }

  /**
   * Updates MCP server tools by comparing old and new tool lists.
   * Only removes tools that are not in the new list and adds tools that are not in the old list.
   * Uses removeTools for batch removal.
   *
   * @return the new tools list to be stored as previousTools
   */
  private suspend fun updateMcpServerTools(
    mcpServer: Server,
    session: ServerSession,
    projectPath: String?,
    previousTools: List<McpTool>?,
    newTools: List<McpTool>
  ): List<McpTool> {
    logger.trace { "updateMcpServerTools" }

    val previousToolNames = previousTools?.map { it.descriptor.name }?.toSet() ?: emptySet()
    val newToolNames = newTools.map { it.descriptor.name }.toSet()

    // Find tools to remove (in previous but not in new)
    val toolsToRemove = previousToolNames - newToolNames
    if (toolsToRemove.isNotEmpty()) {
      logger.trace { "Removing tools from MCP server: $toolsToRemove" }
      mcpServer.removeTools(toolsToRemove.toList())
    }

    // Find tools to add (in new but not in previous)
    val toolNamesToAdd = newToolNames - previousToolNames
    val toolsToAdd = newTools.filter { it.descriptor.name in toolNamesToAdd }
    if (toolsToAdd.isNotEmpty()) {
      logger.trace { "Adding tools to MCP server: ${toolsToAdd.map { it.descriptor.name }}" }
      mcpServer.addTools(toolsToAdd.map { mcpToolToRegisteredTool(it, session, projectPath) })
    }

    return newTools
  }

  internal fun getMcpTools(filter: McpToolFilter = McpToolFilter.AllowAll, useFiltersFromEP: Boolean = true, clientInfo: Implementation? = null, sessionOptions: McpSessionOptions? = null): List<McpTool> {
    return getMcpToolsFiltered(filter, useFiltersFromEP, excludeProviders = emptySet(), clientInfo = clientInfo, sessionOptions = sessionOptions)
  }

  /**
   * Returns MCP tools filtered by all filter providers except those specified in [excludeProviders].
   * This is useful for UI that needs to show tools filtered by some providers but not others
   * (e.g., showing tools for disallow list configuration without applying the disallow list filter itself).
   */
  internal fun getMcpToolsFiltered(
    filter: McpToolFilter = McpToolFilter.AllowAll,
    useFiltersFromEP: Boolean = true,
    excludeProviders: Set<Class<out McpToolFilterProvider>>,
    clientInfo: Implementation? = null,
    sessionOptions: McpSessionOptions? = null,
  ): List<McpTool> {
    val allTools = McpToolsProvider.EP.extensionList.flatMap {
      try {
        it.getTools()
      }
      catch (e: Exception) {
        logger.error("Cannot load tools for $it", e)
        emptyList()
      }
    }
    val filteredByName = allTools.filter { filter.shouldInclude(it.descriptor.name) }
    if (!useFiltersFromEP) {
      return filteredByName
    }
    val filterProviders = McpToolFilterProvider.EP.extensionList
      .filter { provider -> excludeProviders.none { it.isInstance(provider) } }
    val filters = filterProviders.flatMap { it.getFilters(clientInfo, sessionOptions) }
    var context = McpToolFilterProvider.McpToolFilterContext(
      disallowedTools = emptySet(),
      allowedTools = filteredByName.toSet()
    )
    for (filterItem in filters) {
      context = filterItem.modify(context).apply(context)
    }
    return context.allowedTools.toList()
  }

  private suspend fun mcpToolToRegisteredTool(
    mcpTool: McpTool,
    session: ServerSession,
    projectPathFromInitialRequest: String?,
  ): RegisteredTool {
    val tool = mcpTool.toSdkTool()
    return RegisteredTool(tool) { request ->
      val httpRequest = currentCoroutineContext().httpRequestOrNull
      val projectPathFromHeaders =
        httpRequest?.headers?.get(IJ_MCP_SERVER_PROJECT_PATH) ?: (request.meta?.get(IJ_MCP_SERVER_PROJECT_PATH) as? JsonPrimitive)?.content
        ?: projectPathFromInitialRequest
      val projectPathFromMcpRequest = (request.arguments?.get(projectPathParameterName) as? JsonPrimitive)?.content
      val project = try {
        val sessionRoots = sessionRoots[session.sessionId]
        val projectFromRootList = sessionRoots?.let { findMostRelevantProjectForRoots(sessionRoots) }
        logger.trace { "Locating project for session: ${session.sessionId}, roots: $sessionRoots, $projectPathParameterName: $projectPathFromMcpRequest, projectPathFromHeaders: $projectPathFromHeaders" }
        if (projectFromRootList != null) {
          logger.trace { "Project $projectFromRootList from roots list: $sessionRoots" }
          // prefer project from list of roots
          projectFromRootList
        } else if (!projectPathFromMcpRequest.isNullOrBlank()) {
          logger.trace { "Project path specified in MCP request: $projectPathFromMcpRequest" }
          // project from mcp argument first (may hallucinate)
          findMostRelevantProject(projectPathFromMcpRequest)
          ?: throw noSuitableProjectError("`$projectPathParameterName`=`$projectPathFromMcpRequest` doesn't correspond to any open project.")
        }
        else if (!projectPathFromHeaders.isNullOrBlank()) {
          logger.trace { "Project path specified in MCP request headers: $projectPathFromHeaders" }
          // then from headers
          findMostRelevantProject(projectPathFromHeaders)
          ?: throw noSuitableProjectError("Project path specified via header variable `$IJ_MCP_SERVER_PROJECT_PATH`=`$projectPathFromHeaders` doesn't correspond to any open project.")
        }
        else {
          null
        }
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
        return@RegisteredTool McpToolCallResult.error(errorMessage = mcpError.mcpErrorText,
                                                      structuredContent = mcpError.mcpErrorStructureContent).toSdkToolCallResult()
      }
      catch (e: Throwable) {
        logger.error("Failed to determine project for MCP tool call by provided arguments", e)
        return@RegisteredTool McpToolCallResult.error(errorMessage = e.message ?: "Unknown error", structuredContent = null)
          .toSdkToolCallResult()
      }

      val authToken = httpRequest?.headers[IJ_MCP_AUTH_TOKEN]
      val headersWithoutAuthToken = httpRequest?.headers?.toMap()?.let { it - IJ_MCP_AUTH_TOKEN }

      val sessionOptions = getSessionOptions(authToken)

      val vfsEvent = CopyOnWriteArrayList<VFileEvent>()
      val initialDocumentContents = ConcurrentHashMap<Document, String>()
      val clientVersion = session.clientVersion ?: Implementation("Unknown MCP client", "Unknown version")

      val sessionId = session.sessionId
      val additionalData = McpCallInfo(
        callId = callId.getAndAdd(1),
        clientInfo = ClientInfo(clientVersion.name, clientVersion.version),
        project = project,
        mcpToolDescriptor = mcpTool.descriptor,
        rawArguments = request.arguments ?: EmptyJsonObject,
        meta = request.meta?.json ?: EmptyJsonObject,
        mcpSessionOptions = sessionOptions,
        headers = headersWithoutAuthToken ?: emptyMap(),
      )

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
            .setAllAttributes(Attributes.builder()
                                .put("mcp.tool.name", mcpTool.descriptor.name)
                                .put("mcp.client.name", clientVersion.name)
                                .put("mcp.client.version", clientVersion.version)
                                .put("mcp.call.id", additionalData.callId.toLong())
                                .put("mcp.session.id", sessionId)
                                .build())
            .startSpan()

          try {
            span.makeCurrent().use {
              val sideEffectEvents = mutableListOf<McpToolSideEffectEvent>()
              @Suppress("IncorrectCancellationExceptionHandling")
              try {
                application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                  .beforeMcpToolCall(mcpTool.descriptor, additionalData)

                logger.trace { "Start calling tool '${mcpTool.descriptor.name}'. Arguments: ${request.arguments}" }

                span.addEvent("mcp.tool.call.started",
                              Attributes.of(
                                AttributeKey.stringKey("arguments.size"),
                                request.arguments?.size?.toString() ?: "0"
                              ))

                val result = mcpTool.call(request.arguments ?: EmptyJsonObject)

                logger.trace {
                  "Tool call successful '${mcpTool.descriptor.name}'. Result: ${
                    result.content.joinToString("\n") { it.toString() }
                  }"
                }

                span.addEvent("mcp.tool.call.completed",
                              Attributes.of(
                                AttributeKey.stringKey("result.content.count"),
                                result.content.size.toString()
                              ))
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
                        }
                        else {
                          val newContent = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text } ?: continue
                          sideEffectEvents.add(FileCreatedEvent(virtualFile, newContent))
                        }
                      }
                      is VFileDeleteEvent -> {
                        val virtualFile = event.file
                        if (virtualFile.isDirectory) {
                          sideEffectEvents.add(DirectoryDeletedEvent(virtualFile))
                        }
                        else {
                          val document = readAction { FileDocumentManager.getInstance().getDocument(virtualFile) } ?: continue
                          val oldContent = initialDocumentContents[document]
                          sideEffectEvents.add(FileDeletedEvent(virtualFile, oldContent))
                        }
                      }
                      is VFileCopyEvent -> {
                        val createdFile = event.findCreatedFile() ?: continue
                        val newContent = readAction { FileDocumentManager.getInstance().getDocument(createdFile)?.text } ?: continue
                        sideEffectEvents.add(FileCreatedEvent(createdFile, newContent))
                      }
                      is VFileContentChangeEvent -> {
                        // reported in documents loop
                        if (processedChangedFiles.contains(event.file)) continue
                        val virtualFile = event.file
                        val newContent = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)?.text } ?: continue
                        // Important: there may be a case when file is changed via low level change (like File.replaceText).
                        // in this case we don't track the old content, because it may be heavy, it requires loading the file in
                        // AsyncFileListener above and decoding with encoding etc. The file can be binary etc.
                        sideEffectEvents.add(FileContentChangeEvent(virtualFile, oldContent = null, newContent = newContent))
                      }
                    }
                  }

                }
                catch (ce: CancellationException) {
                  throw ce
                }
                catch (t: Throwable) {
                  logger.error("Failed to process changed documents after calling MCP tool ${mcpTool.descriptor.name}",
                               t)
                }
                application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                  .afterMcpToolCall(mcpTool.descriptor, sideEffectEvents, null, additionalData)
                result
              }
              catch (ce: CancellationException) {
                val message = "MCP tool call has been cancelled likely by a user interaction: ${ce.message}"
                logger.traceThrowable { CancellationException(message, ce) }
                span.setStatus(StatusCode.ERROR, message)
                application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                  .afterMcpToolCall(mcpTool.descriptor, sideEffectEvents, ce, additionalData)
                McpToolCallResult.error(message)
              }
              catch (mcpException: McpExpectedError) {
                logger.traceThrowable { mcpException }
                span.setStatus(StatusCode.ERROR, "MCP expected error: ${mcpException.mcpErrorText}")
                application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                  .afterMcpToolCall(mcpTool.descriptor, sideEffectEvents, mcpException, additionalData)
                McpToolCallResult.error(mcpException.mcpErrorText, mcpException.mcpErrorStructureContent)
              }
              catch (t: Throwable) {
                val errorMessage = "MCP tool call has been failed: ${t.message}"
                logger.error(t)
                span.setStatus(StatusCode.ERROR, errorMessage)
                application.messageBus.syncPublisher(ToolCallListener.TOPIC)
                  .afterMcpToolCall(mcpTool.descriptor, sideEffectEvents, t, additionalData)
                McpToolCallResult.error(errorMessage)
              }
              finally {
                if (sideEffectEvents.isNotEmpty()) {
                  withContext(Dispatchers.EDT) {
                    writeIntentReadAction {
                      FileDocumentManager.getInstance().saveAllDocuments()
                    }
                  }
                }
                span.setAllAttributes(Attributes.builder()
                                        .put("mcp.side_effects.vfs_events", vfsEvent.size.toLong())
                                        .put("mcp.side_effects.document_changes", initialDocumentContents.size.toLong())
                                        .build())
                McpServerCounterUsagesCollector.reportMcpCall(mcpTool.descriptor)
              }
            }
          }
          finally {
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