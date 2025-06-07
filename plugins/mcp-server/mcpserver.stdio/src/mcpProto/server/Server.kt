package com.intellij.mcpserver.stdio.mcpProto.server

import com.intellij.mcpserver.stdio.mcpProto.CallToolRequest
import com.intellij.mcpserver.stdio.mcpProto.CallToolResult
import com.intellij.mcpserver.stdio.mcpProto.ClientCapabilities
import com.intellij.mcpserver.stdio.mcpProto.CreateMessageRequest
import com.intellij.mcpserver.stdio.mcpProto.CreateMessageResult
import com.intellij.mcpserver.stdio.mcpProto.EmptyJsonObject
import com.intellij.mcpserver.stdio.mcpProto.EmptyRequestResult
import com.intellij.mcpserver.stdio.mcpProto.GetPromptRequest
import com.intellij.mcpserver.stdio.mcpProto.GetPromptResult
import com.intellij.mcpserver.stdio.mcpProto.Implementation
import com.intellij.mcpserver.stdio.mcpProto.InitializeRequest
import com.intellij.mcpserver.stdio.mcpProto.InitializeResult
import com.intellij.mcpserver.stdio.mcpProto.InitializedNotification
import com.intellij.mcpserver.stdio.mcpProto.KotlinLogging
import com.intellij.mcpserver.stdio.mcpProto.LATEST_PROTOCOL_VERSION
import com.intellij.mcpserver.stdio.mcpProto.ListPromptsRequest
import com.intellij.mcpserver.stdio.mcpProto.ListPromptsResult
import com.intellij.mcpserver.stdio.mcpProto.ListResourceTemplatesRequest
import com.intellij.mcpserver.stdio.mcpProto.ListResourceTemplatesResult
import com.intellij.mcpserver.stdio.mcpProto.ListResourcesRequest
import com.intellij.mcpserver.stdio.mcpProto.ListResourcesResult
import com.intellij.mcpserver.stdio.mcpProto.ListRootsRequest
import com.intellij.mcpserver.stdio.mcpProto.ListRootsResult
import com.intellij.mcpserver.stdio.mcpProto.ListToolsRequest
import com.intellij.mcpserver.stdio.mcpProto.ListToolsResult
import com.intellij.mcpserver.stdio.mcpProto.LoggingMessageNotification
import com.intellij.mcpserver.stdio.mcpProto.Method
import com.intellij.mcpserver.stdio.mcpProto.PingRequest
import com.intellij.mcpserver.stdio.mcpProto.Prompt
import com.intellij.mcpserver.stdio.mcpProto.PromptArgument
import com.intellij.mcpserver.stdio.mcpProto.PromptListChangedNotification
import com.intellij.mcpserver.stdio.mcpProto.ReadResourceRequest
import com.intellij.mcpserver.stdio.mcpProto.ReadResourceResult
import com.intellij.mcpserver.stdio.mcpProto.Resource
import com.intellij.mcpserver.stdio.mcpProto.ResourceListChangedNotification
import com.intellij.mcpserver.stdio.mcpProto.ResourceUpdatedNotification
import com.intellij.mcpserver.stdio.mcpProto.SUPPORTED_PROTOCOL_VERSIONS
import com.intellij.mcpserver.stdio.mcpProto.ServerCapabilities
import com.intellij.mcpserver.stdio.mcpProto.Tool
import com.intellij.mcpserver.stdio.mcpProto.ToolListChangedNotification
import com.intellij.mcpserver.stdio.mcpProto.shared.Protocol
import com.intellij.mcpserver.stdio.mcpProto.shared.ProtocolOptions
import com.intellij.mcpserver.stdio.mcpProto.shared.RequestOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

/**
 * Configuration options for the MCP server.
 *
 * @property capabilities The capabilities this server supports.
 * @property enforceStrictCapabilities Whether to strictly enforce capabilities when interacting with clients.
 */
public class ServerOptions(
  public val capabilities: ServerCapabilities,
  enforceStrictCapabilities: Boolean = true,
) : ProtocolOptions(enforceStrictCapabilities = enforceStrictCapabilities)

/**
 * An MCP server on top of a pluggable transport.
 *
 * This server automatically responds to the initialization flow as initiated by the client.
 * You can register tools, prompts, and resources using [addTool], [addPrompt], and [addResource].
 * The server will then automatically handle listing and retrieval requests from the client.
 *
 * @param serverInfo Information about this server implementation (name, version).
 * @param options Configuration options for the server.
 */
public open class Server(
  private val serverInfo: Implementation,
  options: ServerOptions,
) : Protocol(options) {
    private var _onInitialized: (() -> Unit) = {}
    private var _onClose: () -> Unit = {}

    /**
     * The client's reported capabilities after initialization.
     */
    public var clientCapabilities: ClientCapabilities? = null
        private set

    /**
     * The client's version information after initialization.
     */
    public var clientVersion: Implementation? = null
        private set

    private val capabilities: ServerCapabilities = options.capabilities

    private val tools = mutableMapOf<String, RegisteredTool>()
    private val prompts = mutableMapOf<String, RegisteredPrompt>()
    private val resources = mutableMapOf<String, RegisteredResource>()

    init {
        logger.debug { "Initializing MCP server with capabilities: $capabilities" }

        // Core protocol handlers
        setRequestHandler<InitializeRequest>(Method.Defined.Initialize) { request, _ ->
            handleInitialize(request)
        }
        setNotificationHandler<InitializedNotification>(Method.Defined.NotificationsInitialized) {
            _onInitialized()
            CompletableDeferred(Unit)
        }

        // Internal handlers for tools
        if (capabilities.tools != null) {
            setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { _, _ ->
                handleListTools()
            }
            setRequestHandler<CallToolRequest>(Method.Defined.ToolsCall) { request, _ ->
                handleCallTool(request)
            }
        }

        // Internal handlers for prompts
        if (capabilities.prompts != null) {
            setRequestHandler<ListPromptsRequest>(Method.Defined.PromptsList) { _, _ ->
                handleListPrompts()
            }
            setRequestHandler<GetPromptRequest>(Method.Defined.PromptsGet) { request, _ ->
                handleGetPrompt(request)
            }
        }

        // Internal handlers for resources
        if (capabilities.resources != null) {
            setRequestHandler<ListResourcesRequest>(Method.Defined.ResourcesList) { _, _ ->
                handleListResources()
            }
            setRequestHandler<ReadResourceRequest>(Method.Defined.ResourcesRead) { request, _ ->
                handleReadResource(request)
            }
            setRequestHandler<ListResourceTemplatesRequest>(Method.Defined.ResourcesTemplatesList) { _, _ ->
                handleListResourceTemplates()
            }
        }
    }

    /**
     * Registers a callback to be invoked when the server has completed initialization.
     */
    public fun onInitialized(block: () -> Unit) {
        val old = _onInitialized
        _onInitialized = {
            old()
            block()
        }
    }

    /**
     * Registers a callback to be invoked when the server connection is closing.
     */
    public fun onClose(block: () -> Unit) {
        val old = _onClose
        _onClose = {
            old()
            block()
        }
    }

    /**
     * Called when the server connection is closing.
     */
    override fun onClose() {
        logger.info { "Server connection closing" }
        _onClose()
    }

    /**
     * Registers a single tool. The client can then call this tool.
     *
     * @param name The name of the tool.
     * @param description A human-readable description of what the tool does.
     * @param inputSchema The expected input schema for the tool.
     * @param handler A suspend function that handles executing the tool when called by the client.
     * @throws IllegalStateException If the server does not support tools.
     */
    public fun addTool(
      name: String,
      description: String,
      inputSchema: Tool.Input = Tool.Input(),
      handler: suspend (CallToolRequest) -> CallToolResult
    ) {
        if (capabilities.tools == null) {
            logger.error { "Failed to add tool '$name': Server does not support tools capability" }
            throw IllegalStateException("Server does not support tools capability. Enable it in ServerOptions.")
        }
        logger.info { "Registering tool: $name" }
        tools[name] = RegisteredTool(Tool(name, description, inputSchema), handler)
    }

    /**
     * Registers multiple tools at once.
     *
     * @param toolsToAdd A list of [RegisteredTool] objects representing the tools to register.
     * @throws IllegalStateException If the server does not support tools.
     */
    public fun addTools(toolsToAdd: List<RegisteredTool>) {
        if (capabilities.tools == null) {
            logger.error { "Failed to add tools: Server does not support tools capability" }
            throw IllegalStateException("Server does not support tools capability.")
        }
        logger.info { "Registering ${toolsToAdd.size} tools" }
        for (rt in toolsToAdd) {
            logger.debug { "Registering tool: ${rt.tool.name}" }
            tools[rt.tool.name] = rt
        }
    }

    /**
     * Removes a single tool by name.
     *
     * @param name The name of the tool to remove.
     * @return True if the tool was removed, false if it wasn't found.
     * @throws IllegalStateException If the server does not support tools.
     */
    public fun removeTool(name: String): Boolean {
        if (capabilities.tools == null) {
            logger.error { "Failed to remove tool '$name': Server does not support tools capability" }
            throw IllegalStateException("Server does not support tools capability.")
        }
        logger.info { "Removing tool: $name" }
        val removed = tools.remove(name) != null
        logger.debug {
            if (removed) {
                "Tool removed: $name"
            } else {
                "Tool not found: $name"
            }
        }
        return removed
    }

    /**
     * Removes multiple tools at once.
     *
     * @param toolNames A list of tool names to remove.
     * @return The number of tools that were successfully removed.
     * @throws IllegalStateException If the server does not support tools.
     */
    public fun removeTools(toolNames: List<String>): Int {
        if (capabilities.tools == null) {
            logger.error { "Failed to remove tools: Server does not support tools capability" }
            throw IllegalStateException("Server does not support tools capability.")
        }
        logger.info { "Removing ${toolNames.size} tools" }
        var removedCount = 0
        for (name in toolNames) {
            logger.debug { "Removing tool: $name" }
            if (tools.remove(name) != null) {
                removedCount++
            }
        }
        logger.info {
            if (removedCount > 0) {
              "Removed $removedCount tools"
            } else {
              "No tools were removed"
            }
        }
        return removedCount
    }

    /**
     * Registers a single prompt. The client can then retrieve the prompt.
     *
     * @param prompt A [Prompt] object describing the prompt.
     * @param promptProvider A suspend function that returns the prompt content when requested by the client.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun addPrompt(prompt: Prompt, promptProvider: suspend (GetPromptRequest) -> GetPromptResult) {
        if (capabilities.prompts == null) {
            logger.error { "Failed to add prompt '${prompt.name}': Server does not support prompts capability" }
            throw IllegalStateException("Server does not support prompts capability.")
        }
        logger.info { "Registering prompt: ${prompt.name}" }
        prompts[prompt.name] = RegisteredPrompt(prompt, promptProvider)
    }

    /**
     * Registers a single prompt by constructing a [Prompt] from given parameters.
     *
     * @param name The name of the prompt.
     * @param description An optional human-readable description of the prompt.
     * @param arguments An optional list of [PromptArgument] that the prompt accepts.
     * @param promptProvider A suspend function that returns the prompt content when requested.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun addPrompt(
      name: String,
      description: String? = null,
      arguments: List<PromptArgument>? = null,
      promptProvider: suspend (GetPromptRequest) -> GetPromptResult
    ) {
        val prompt = Prompt(name = name, description = description, arguments = arguments)
        addPrompt(prompt, promptProvider)
    }

    /**
     * Registers multiple prompts at once.
     *
     * @param promptsToAdd A list of [RegisteredPrompt] objects representing the prompts to register.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun addPrompts(promptsToAdd: List<RegisteredPrompt>) {
        if (capabilities.prompts == null) {
            logger.error { "Failed to add prompts: Server does not support prompts capability" }
            throw IllegalStateException("Server does not support prompts capability.")
        }
        logger.info { "Registering ${promptsToAdd.size} prompts" }
        for (rp in promptsToAdd) {
            logger.debug { "Registering prompt: ${rp.prompt.name}" }
            prompts[rp.prompt.name] = rp
        }
    }

    /**
     * Removes a single prompt by name.
     *
     * @param name The name of the prompt to remove.
     * @return True if the prompt was removed, false if it wasn't found.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun removePrompt(name: String): Boolean {
        if (capabilities.prompts == null) {
            logger.error { "Failed to remove prompt '$name': Server does not support prompts capability" }
            throw IllegalStateException("Server does not support prompts capability.")
        }
        logger.info { "Removing prompt: $name" }
        val removed = prompts.remove(name) != null
        logger.debug {
            if (removed) {
                "Prompt removed: $name"
            } else {
                "Prompt not found: $name"
            }
        }
        return removed
    }

    /**
     * Removes multiple prompts at once.
     *
     * @param promptNames A list of prompt names to remove.
     * @return The number of prompts that were successfully removed.
     * @throws IllegalStateException If the server does not support prompts.
     */
    public fun removePrompts(promptNames: List<String>): Int {
        if (capabilities.prompts == null) {
            logger.error { "Failed to remove prompts: Server does not support prompts capability" }
            throw IllegalStateException("Server does not support prompts capability.")
        }
        logger.info { "Removing ${promptNames.size} prompts" }
        var removedCount = 0
        for (name in promptNames) {
            logger.debug { "Removing prompt: $name" }
            if (prompts.remove(name) != null) {
                removedCount++
            }
        }
        logger.info {
            if (removedCount > 0) {
                "Removed $removedCount prompts"
            } else {
                "No prompts were removed"
            }
        }
        return removedCount
    }

    /**
     * Registers a single resource. The client can then read the resource content.
     *
     * @param uri The URI of the resource.
     * @param name A human-readable name for the resource.
     * @param description A description of the resource's content.
     * @param mimeType The MIME type of the resource content.
     * @param readHandler A suspend function that returns the resource content when read by the client.
     * @throws IllegalStateException If the server does not support resources.
     */
    public fun addResource(
        uri: String,
        name: String,
        description: String,
        mimeType: String = "text/html",
        readHandler: suspend (ReadResourceRequest) -> ReadResourceResult
    ) {
        if (capabilities.resources == null) {
            logger.error { "Failed to add resource '$name': Server does not support resources capability" }
            throw IllegalStateException("Server does not support resources capability.")
        }
        logger.info { "Registering resource: $name ($uri)" }
        resources[uri] = RegisteredResource(Resource(uri, name, description, mimeType), readHandler)
    }

    /**
     * Registers multiple resources at once.
     *
     * @param resourcesToAdd A list of [RegisteredResource] objects representing the resources to register.
     * @throws IllegalStateException If the server does not support resources.
     */
    public fun addResources(resourcesToAdd: List<RegisteredResource>) {
        if (capabilities.resources == null) {
            logger.error { "Failed to add resources: Server does not support resources capability" }
            throw IllegalStateException("Server does not support resources capability.")
        }
        logger.info { "Registering ${resourcesToAdd.size} resources" }
        for (r in resourcesToAdd) {
            logger.debug { "Registering resource: ${r.resource.name} (${r.resource.uri})" }
            resources[r.resource.uri] = r
        }
    }

    /**
     * Removes a single resource by URI.
     *
     * @param uri The URI of the resource to remove.
     * @return True if the resource was removed, false if it wasn't found.
     * @throws IllegalStateException If the server does not support resources.
     */
    public fun removeResource(uri: String): Boolean {
        if (capabilities.resources == null) {
            logger.error { "Failed to remove resource '$uri': Server does not support resources capability" }
            throw IllegalStateException("Server does not support resources capability.")
        }
        logger.info { "Removing resource: $uri" }
        val removed = resources.remove(uri) != null
        logger.debug {
            if (removed) {
                "Resource removed: $uri"
            } else {
                "Resource not found: $uri"
            }
        }
        return removed
    }

    /**
     * Removes multiple resources at once.
     *
     * @param uris A list of resource URIs to remove.
     * @return The number of resources that were successfully removed.
     * @throws IllegalStateException If the server does not support resources.
     */
    public fun removeResources(uris: List<String>): Int {
        if (capabilities.resources == null) {
            logger.error { "Failed to remove resources: Server does not support resources capability" }
            throw IllegalStateException("Server does not support resources capability.")
        }
        logger.info { "Removing ${uris.size} resources" }
        var removedCount = 0
        for (uri in uris) {
            logger.debug { "Removing resource: $uri" }
            if (resources.remove(uri) != null) {
                removedCount++
            }
        }
        logger.info {
            if (removedCount > 0) {
                "Removed $removedCount resources"
            } else {
                "No resources were removed"
            }
        }
        return removedCount
    }

    /**
     * Sends a ping request to the client to check connectivity.
     *
     * @return The result of the ping request.
     * @throws IllegalStateException If for some reason the method is not supported or the connection is closed.
     */
    public suspend fun ping(): EmptyRequestResult {
        return request<EmptyRequestResult>(PingRequest())
    }

    /**
     * Creates a message using the server's sampling capability.
     *
     * @param params The parameters for creating a message.
     * @param options Optional request options.
     * @return The created message result.
     * @throws IllegalStateException If the server does not support sampling or if the request fails.
     */
    public suspend fun createMessage(
      params: CreateMessageRequest,
      options: RequestOptions? = null
    ): CreateMessageResult {
        logger.debug { "Creating message with params: $params" }
        return request<CreateMessageResult>(params, options)
    }

    /**
     * Lists the available "roots" from the client's perspective (if supported).
     *
     * @param params JSON parameters for the request, usually empty.
     * @param options Optional request options.
     * @return The list of roots.
     * @throws IllegalStateException If the server or client does not support roots.
     */
    public suspend fun listRoots(
      params: JsonObject = EmptyJsonObject,
      options: RequestOptions? = null
    ): ListRootsResult {
        logger.debug { "Listing roots with params: $params" }
        return request<ListRootsResult>(ListRootsRequest(params), options)
    }

    /**
     * Sends a logging message notification to the client.
     *
     * @param params The logging message notification parameters.
     */
    public suspend fun sendLoggingMessage(params: LoggingMessageNotification) {
        logger.trace { "Sending logging message: ${params.data}" }
        notification(params)
    }

    /**
     * Sends a resource-updated notification to the client, indicating that a specific resource has changed.
     *
     * @param params Details of the updated resource.
     */
    public suspend fun sendResourceUpdated(params: ResourceUpdatedNotification) {
        logger.debug { "Sending resource updated notification for: ${params.uri}" }
        notification(params)
    }

    /**
     * Sends a notification to the client indicating that the list of resources has changed.
     */
    public suspend fun sendResourceListChanged() {
        logger.debug { "Sending resource list changed notification" }
        notification(ResourceListChangedNotification())
    }

    /**
     * Sends a notification to the client indicating that the list of tools has changed.
     */
    public suspend fun sendToolListChanged() {
        logger.debug { "Sending tool list changed notification" }
        notification(ToolListChangedNotification())
    }

    /**
     * Sends a notification to the client indicating that the list of prompts has changed.
     */
    public suspend fun sendPromptListChanged() {
        logger.debug { "Sending prompt list changed notification" }
        notification(PromptListChangedNotification())
    }

    // --- Internal Handlers ---

    private suspend fun handleInitialize(request: InitializeRequest): InitializeResult {
        logger.info { "Handling initialize request from client ${request.clientInfo}" }
        clientCapabilities = request.capabilities
        clientVersion = request.clientInfo

        val requestedVersion = request.protocolVersion
        val protocolVersion = if (SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)) {
            requestedVersion
        } else {
            logger.warn { "Client requested unsupported protocol version $requestedVersion, falling back to ${LATEST_PROTOCOL_VERSION}" }
          LATEST_PROTOCOL_VERSION
        }

        return InitializeResult(
          protocolVersion = protocolVersion,
          capabilities = capabilities,
          serverInfo = serverInfo
        )
    }

    private suspend fun handleListTools(): ListToolsResult {
        val toolList = tools.values.map { it.tool }
        return ListToolsResult(tools = toolList, nextCursor = null)
    }

    private suspend fun handleCallTool(request: CallToolRequest): CallToolResult {
        logger.debug { "Handling tool call request for tool: ${request.name}" }
        val tool = tools[request.name]
            ?: run {
                logger.error { "Tool not found: ${request.name}" }
                throw IllegalArgumentException("Tool not found: ${request.name}")
            }
        logger.trace { "Executing tool ${request.name} with input: ${request.arguments}" }
        return tool.handler(request)
    }

    private suspend fun handleListPrompts(): ListPromptsResult {
        logger.debug { "Handling list prompts request" }
        return ListPromptsResult(prompts = prompts.values.map { it.prompt })
    }

    private suspend fun handleGetPrompt(request: GetPromptRequest): GetPromptResult {
        logger.debug { "Handling get prompt request for: ${request.name}" }
        val prompt = prompts[request.name]
            ?: run {
                logger.error { "Prompt not found: ${request.name}" }
                throw IllegalArgumentException("Prompt not found: ${request.name}")
            }
        return prompt.messageProvider(request)
    }

    private suspend fun handleListResources(): ListResourcesResult {
        logger.debug { "Handling list resources request" }
        return ListResourcesResult(resources = resources.values.map { it.resource })
    }

    private suspend fun handleReadResource(request: ReadResourceRequest): ReadResourceResult {
        logger.debug { "Handling read resource request for: ${request.uri}" }
        val resource = resources[request.uri]
            ?: run {
                logger.error { "Resource not found: ${request.uri}" }
                throw IllegalArgumentException("Resource not found: ${request.uri}")
            }
        return resource.readHandler(request)
    }

    private suspend fun handleListResourceTemplates(): ListResourceTemplatesResult {
        // If you have resource templates, return them here. For now, return empty.
        return ListResourceTemplatesResult(listOf())
    }

    /**
     * Asserts that the client supports the capability required for the given [method].
     *
     * This method is automatically called by the [Protocol] framework before handling requests.
     * Throws [IllegalStateException] if the capability is not supported.
     *
     * @param method The method for which we are asserting capability.
     */
    override fun assertCapabilityForMethod(method: Method) {
        logger.trace { "Asserting capability for method: ${method.value}" }
        when (method.value) {
            "sampling/createMessage" -> {
                if (clientCapabilities?.sampling == null) {
                    logger.error { "Client capability assertion failed: sampling not supported" }
                    throw IllegalStateException("Client does not support sampling (required for ${method.value})")
                }
            }

            "roots/list" -> {
                if (clientCapabilities?.roots == null) {
                    throw IllegalStateException("Client does not support listing roots (required for ${method.value})")
                }
            }

            "ping" -> {
                // No specific capability required
            }
        }
    }

    /**
     * Asserts that the server can handle the specified notification method.
     *
     * Throws [IllegalStateException] if the server does not have the capabilities required to handle this notification.
     *
     * @param method The notification method.
     */
    override fun assertNotificationCapability(method: Method) {
        logger.trace { "Asserting notification capability for method: ${method.value}" }
        when (method.value) {
            "notifications/message" -> {
                if (capabilities.logging == null) {
                    logger.error { "Server capability assertion failed: logging not supported" }
                    throw IllegalStateException("Server does not support logging (required for ${method.value})")
                }
            }

            "notifications/resources/updated",
            "notifications/resources/list_changed" -> {
                if (capabilities.resources == null) {
                    throw IllegalStateException("Server does not support notifying about resources (required for ${method.value})")
                }
            }

            "notifications/tools/list_changed" -> {
                if (capabilities.tools == null) {
                    throw IllegalStateException("Server does not support notifying of tool list changes (required for ${method.value})")
                }
            }

            "notifications/prompts/list_changed" -> {
                if (capabilities.prompts == null) {
                    throw IllegalStateException("Server does not support notifying of prompt list changes (required for ${method.value})")
                }
            }

            "notifications/cancelled",
            "notifications/progress" -> {
                // Always allowed
            }
        }
    }

    /**
     * Asserts that the server can handle the specified request method.
     *
     * Throws [IllegalStateException] if the server does not have the capabilities required to handle this request.
     *
     * @param method The request method.
     */
    override fun assertRequestHandlerCapability(method: Method) {
        logger.trace { "Asserting request handler capability for method: ${method.value}" }
        when (method.value) {
            "sampling/createMessage" -> {
                if (capabilities.sampling == null) {
                    logger.error { "Server capability assertion failed: sampling not supported" }
                    throw IllegalStateException("Server does not support sampling (required for $method)")
                }
            }

            "logging/setLevel" -> {
                if (capabilities.logging == null) {
                    throw IllegalStateException("Server does not support logging (required for $method)")
                }
            }

            "prompts/get",
            "prompts/list" -> {
                if (capabilities.prompts == null) {
                    throw IllegalStateException("Server does not support prompts (required for $method)")
                }
            }

            "resources/list",
            "resources/templates/list",
            "resources/read" -> {
                if (capabilities.resources == null) {
                    throw IllegalStateException("Server does not support resources (required for $method)")
                }
            }

            "tools/call",
            "tools/list" -> {
                if (capabilities.tools == null) {
                    throw IllegalStateException("Server does not support tools (required for $method)")
                }
            }

            "ping", "initialize" -> {
                // No capability required
            }
        }
    }
}

/**
 * A wrapper class representing a registered tool on the server.
 *
 * @property tool The tool definition.
 * @property handler A suspend function to handle the tool call requests.
 */
public data class RegisteredTool(
  val tool: Tool,
  val handler: suspend (CallToolRequest) -> CallToolResult
)

/**
 * A wrapper class representing a registered prompt on the server.
 *
 * @property prompt The prompt definition.
 * @property messageProvider A suspend function that returns the prompt content when requested by the client.
 */
public data class RegisteredPrompt(
  val prompt: Prompt,
  val messageProvider: suspend (GetPromptRequest) -> GetPromptResult
)

/**
 * A wrapper class representing a registered resource on the server.
 *
 * @property resource The resource definition.
 * @property readHandler A suspend function to handle read requests for this resource.
 */
public data class RegisteredResource(
  val resource: Resource,
  val readHandler: suspend (ReadResourceRequest) -> ReadResourceResult
)
