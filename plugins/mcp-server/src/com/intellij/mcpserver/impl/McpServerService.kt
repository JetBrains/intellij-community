package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolFilter
import com.intellij.mcpserver.McpToolFilterProvider
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.impl.util.network.findFirstFreePort
import com.intellij.mcpserver.impl.util.network.installHostValidation
import com.intellij.mcpserver.impl.util.network.installHttpRequestPropagation
import com.intellij.mcpserver.impl.util.network.mcpPatched
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.stdio.IJ_MCP_ALLOWED_TOOLS
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.mcpserver.toolsets.general.UniversalToolset
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds


private val logger = logger<McpServerService>()
internal val IJ_MCP_AUTH_TOKEN: String = ::IJ_MCP_AUTH_TOKEN.name

open class McpServerService(val cs: CoroutineScope) {
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
    val toolFilter: McpToolFilter? = null,
    val localAgentId: String? = null,
    val invocationMode: McpSessionInvocationMode? = null,
  ) {
    @Deprecated("ABI compat with 261.22158 that doesn't have `localAgentId`", level = DeprecationLevel.HIDDEN)
    constructor(
      commandExecutionMode: AskCommandExecutionMode,
      toolFilter: McpToolFilter = McpToolFilter.AllowAll,
    ) : this(commandExecutionMode, toolFilter, null)
  }

  companion object {
    fun getInstance(): McpServerService = service()
    suspend fun getInstanceAsync(): McpServerService = serviceAsync()

    internal val callId = AtomicInteger(0)
  }

  internal val toolsStateProvider = McpToolsListProvider(cs)
  
  private val server = MutableStateFlow(startGlobalServerIfEnabled())

  private class ServerAndCount(var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>?, var userCount: Int)

  private val privateServer: ServerAndCount = ServerAndCount(null, 0)
  private val privateServerMutex = Mutex()

  private val activeAuthorizedSessions = ConcurrentHashMap<String, McpSessionOptions>()

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
                withTimeout(2000.milliseconds) {
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

  open val port: Int
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
        logger.trace { "Starting session" }

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
        val useFiltersFromEP = allowedToolsFromHeader.isNullOrEmpty()
        // if no header provided, use the existing filter from sessionOptions
        val sessionOptions = if (headerFilter != null) {
          McpSessionOptions(baseSessionOptions.commandExecutionMode, headerFilter, baseSessionOptions.localAgentId)
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

        // Create session-specific MCP tools manager
        val sessionToolsManager = McpSessionHandler(
          parentScope = cs,
          sessionOptions = sessionOptions,
          mcpServerService = this@McpServerService,
          mcpServer = mcpServer,
          projectPathFromInitialRequest = projectPath,
          useFiltersFromEP = useFiltersFromEP,
        )

        val session = sessionToolsManager.createAndInitializeSession(transport, this)
        //session.setRequestHandler<LoggingMessageNotification.SetLevelRequest>(Method.Defined.LoggingSetLevel) { request, extra ->
        //  // Workaround inspector failure
        //  return@setRequestHandler EmptyRequestResult()
        //}

        return@mcpPatched session
      }
    }.start(wait = false)
  }

  internal fun getMcpTools(filter: McpToolFilter? = null, useFiltersFromEP: Boolean = true, clientInfo: Implementation? = null, sessionOptions: McpSessionOptions? = null, invocationMode: McpToolInvocationMode = McpToolInvocationMode.DIRECT): List<McpTool> {
    return getMcpToolsFiltered(filter, useFiltersFromEP, excludeProviders = emptySet(), clientInfo = clientInfo, sessionOptions = sessionOptions, invocationMode = invocationMode)
  }
  
  internal fun getAllMcpTools(): List<McpTool> {
    return toolsStateProvider.allTools.value
  }

  /**
   * Checks if there are any active MCP tools available after applying the filter.
   * 
   * @param filter The filter to apply to the tools
   * @return true if at least one MCP tool is available after filtering, false otherwise
   */
  fun hasActiveMcpTools(filter: McpToolFilter?): Boolean {
    return getMcpTools(filter = filter).isNotEmpty()
  }

  /**
   * Returns MCP tools filtered by all filter providers except those specified in [excludeProviders].
   * This is useful for UI that needs to show tools filtered by some providers but not others
   * (e.g., showing tools for disallow list configuration without applying the disallow-list filter itself).
   */
  internal fun getMcpToolsFiltered(
    filter: McpToolFilter? = null,
    useFiltersFromEP: Boolean = true,
    excludeProviders: Set<Class<out McpToolFilterProvider>>,
    clientInfo: Implementation? = null,
    sessionOptions: McpSessionOptions? = null,
    invocationMode: McpToolInvocationMode = McpToolInvocationMode.DIRECT,
  ): List<McpTool> {
    val allTools = getAllMcpTools()
    val filterAdjusted = when(invocationMode) {
      McpToolInvocationMode.DIRECT -> filter ?: McpToolFilter.AllowAll
      McpToolInvocationMode.VIA_ROUTER -> filter ?: McpToolFilter.AllowAll
      McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED -> McpToolFilter.ProhibitAll
    }

    val routerToolName = UniversalToolset::execute_tool.name
    if (!useFiltersFromEP) {
      return allTools.filter { tool ->
        val isRouterTool = tool.descriptor.name == routerToolName
        val shouldIncludeRouter = invocationMode == McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED
        val shouldExcludeRouter = invocationMode == McpToolInvocationMode.DIRECT || invocationMode == McpToolInvocationMode.VIA_ROUTER
        
        when {
          isRouterTool && shouldExcludeRouter -> false
          isRouterTool && shouldIncludeRouter -> true
          else -> filterAdjusted.shouldInclude(tool)
        }
      }
    }
    val filterProviders = McpToolFilterProvider.EP.extensionList
      .filter { provider -> excludeProviders.none { it.isInstance(provider) } }
    // Start with all tools in ON_DEMAND state
    val context = McpToolFilterProvider.McpToolFilterContext(allTools)
    if (invocationMode == McpToolInvocationMode.DIRECT || invocationMode == McpToolInvocationMode.VIA_ROUTER) {
      context.turnOff { it.descriptor.name == routerToolName }
    }
    else {
      context.turnOn { it.descriptor.name == routerToolName }
    }
    
    // Apply filter providers (can move ON_DEMAND → ON/OFF, or ON → OFF)
    for (filterProvider in filterProviders) {
      filterProvider.applyFilters(context, clientInfo, sessionOptions, invocationMode)
    }
    
    // Apply the filter parameter ONLY to ON_DEMAND tools
    // Tools that pass the filter are included, tools already in ON state are also included
    val includedOnDemandTools = context.onDemandTools.filter { filterAdjusted.shouldInclude(it) }
    
    // Return tools that are either ON or ON_DEMAND and pass the filter
    return (context.onTools + includedOnDemandTools).toList()
  }

}
