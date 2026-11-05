package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpToolFilter
import com.intellij.mcpserver.elicitation.McpElicitationKind
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceOrNull
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface McpServerService {
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
    /**
     * Names the chat this session serves, for `McpCallOwnerTelemetryProvider` to resolve into analytics ids.
     *
     * Deliberately not [localAgentId]: that one already means three things, and the two surfaces disagree on which.
     * AIR puts a thread id there and AI Assistant an agent type, so it identifies a chat on one surface only.
     */
    var ownerKey: String? = null
      private set

    var clientTags: Set<String> = emptySet()
      private set

    var elicitationKind: McpElicitationKind? = null
      private set

    /**
     * Lists the router tool only while the session has a router-only tool, a tool that the agent can reach only through
     * the router. False keeps the default contract, in which the session always lists the router.
     */
    var omitsUnusedRouterTool: Boolean = false
      private set

    constructor(
      commandExecutionMode: AskCommandExecutionMode,
      toolFilter: McpToolFilter?,
      localAgentId: String?,
      invocationMode: McpSessionInvocationMode?,
      elicitationKind: McpElicitationKind?,
    ) : this(commandExecutionMode, toolFilter, localAgentId, invocationMode) {
      this.elicitationKind = elicitationKind
    }

    constructor(
      commandExecutionMode: AskCommandExecutionMode,
      toolFilter: McpToolFilter?,
      localAgentId: String?,
      invocationMode: McpSessionInvocationMode?,
      elicitationKind: McpElicitationKind?,
      clientTags: Set<String>,
    ) : this(commandExecutionMode, toolFilter, localAgentId, invocationMode, elicitationKind) {
      this.clientTags = clientTags.toSet()
    }

    /** Returns a copy naming the chat this session serves. A blank key is no key. */
    fun withOwnerKey(ownerKey: String?): McpSessionOptions =
      copy().also { it.ownerKey = ownerKey?.takeIf(String::isNotBlank) }

    /** Returns a copy that lists the router tool only while the session has a router-only tool. See [omitsUnusedRouterTool]. */
    @ApiStatus.Internal
    fun withUnusedRouterToolOmitted(): McpSessionOptions =
      copy().also { it.omitsUnusedRouterTool = true }

    internal fun withToolFilter(toolFilter: McpToolFilter): McpSessionOptions =
      copy(toolFilter = toolFilter)

    internal fun withClientTags(clientTags: Set<String>): McpSessionOptions =
      copy(clientTags = clientTags)

    private fun copy(
      toolFilter: McpToolFilter? = this.toolFilter,
      clientTags: Set<String> = this.clientTags,
    ): McpSessionOptions {
      return McpSessionOptions(
        commandExecutionMode = commandExecutionMode,
        toolFilter = toolFilter,
        localAgentId = localAgentId,
        invocationMode = invocationMode,
        elicitationKind = elicitationKind,
        clientTags = clientTags,
      ).also {
        it.ownerKey = ownerKey
        it.omitsUnusedRouterTool = omitsUnusedRouterTool
      }
    }

    @Deprecated("ABI compat with 261.22158 that doesn't have `localAgentId`", level = DeprecationLevel.HIDDEN)
    constructor(
      commandExecutionMode: AskCommandExecutionMode,
      toolFilter: McpToolFilter = McpToolFilter.AllowAll,
    ) : this(commandExecutionMode, toolFilter, null)
  }

  companion object {
    fun getInstance(): McpServerService = service()
    fun getInstanceIfCreated(): McpServerService? = serviceOrNull()
    suspend fun getInstanceAsync(): McpServerService = serviceAsync()
  }

  val isRunning: Boolean
  val serverSseUrl: String
  val serverStreamUrl: String
  val port: Int

  fun start()

  fun stop()

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
  )
}
