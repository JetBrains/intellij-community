@file:Suppress("unused", "EnumEntryName")

package com.intellij.mcpserver.stdio.mcpProto

import com.intellij.mcpserver.stdio.mcpProto.shared.McpJson
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.jvm.JvmInline

public const val LATEST_PROTOCOL_VERSION: String = "2024-11-05"

public val SUPPORTED_PROTOCOL_VERSIONS: Array<String> = arrayOf(
    LATEST_PROTOCOL_VERSION,
    "2024-10-07",
)

public const val JSONRPC_VERSION: String = "2.0"

@OptIn(ExperimentalAtomicApi::class)
private val REQUEST_MESSAGE_ID: AtomicLong = AtomicLong(0L)

// copied from com.intellij.mcpserver.Response
@Serializable
data class Response(
  val status: String? = null,
  val error: String? = null
)

/**
 * A progress token, used to associate progress notifications with the original request.
 * Stores message ID.
 */
public typealias ProgressToken = RequestId

/**
 * An opaque token used to represent a cursor for pagination.
 */
public typealias Cursor = String

/**
 * Represents an entity that includes additional metadata in its responses.
 */
@Serializable
public sealed interface WithMeta {
    /**
     * The protocol reserves this result property
     * to allow clients and servers to attach additional metadata to their responses.
     */
    @Suppress("PropertyName")
    public val _meta: JsonObject

    public companion object {
        public val Empty: CustomMeta = CustomMeta()
    }
}

/**
 * An implementation of [WithMeta] containing custom metadata.
 *
 * @param _meta The JSON object holding metadata. Defaults to an empty JSON object.
 */
@Serializable
public class CustomMeta(override val _meta: JsonObject = EmptyJsonObject) : WithMeta

/**
 * Represents a method in the protocol, which can be predefined or custom.
 */
@Serializable(with = RequestMethodSerializer::class)
public sealed interface Method {
    public val value: String

    /**
     * Enum of predefined methods supported by the protocol.
     */
    @Serializable
    public enum class Defined(override val value: String) : Method {
        Initialize("initialize"),
        Ping("ping"),
        ResourcesList("resources/list"),
        ResourcesTemplatesList("resources/templates/list"),
        ResourcesRead("resources/read"),
        ResourcesSubscribe("resources/subscribe"),
        ResourcesUnsubscribe("resources/unsubscribe"),
        PromptsList("prompts/list"),
        PromptsGet("prompts/get"),
        NotificationsCancelled("notifications/cancelled"),
        NotificationsInitialized("notifications/initialized"),
        NotificationsProgress("notifications/progress"),
        NotificationsMessage("notifications/message"),
        NotificationsResourcesUpdated("notifications/resources/updated"),
        NotificationsResourcesListChanged("notifications/resources/list_changed"),
        NotificationsToolsListChanged("notifications/tools/list_changed"),
        NotificationsRootsListChanged("notifications/roots/list_changed"),
        NotificationsPromptsListChanged("notifications/prompts/list_changed"),
        ToolsList("tools/list"),
        ToolsCall("tools/call"),
        LoggingSetLevel("logging/setLevel"),
        SamplingCreateMessage("sampling/createMessage"),
        CompletionComplete("completion/complete"),
        RootsList("roots/list")
    }

    /**
     * Represents a custom method defined by the user.
     */
    @Serializable
    public data class Custom(override val value: String) : Method
}

/**
 * Represents a request in the protocol.
 */
@Serializable(with = RequestPolymorphicSerializer::class)
public sealed interface Request {
    public val method: Method
}

/**
 * Converts the request to a JSON-RPC request.
 *
 * @return The JSON-RPC request representation.
 */
internal fun Request.toJSON(): JSONRPCRequest {
    return JSONRPCRequest(
        method = method.value,
        params = McpJson.encodeToJsonElement(this),
        jsonrpc = JSONRPC_VERSION,
    )
}

/**
 * Decodes a JSON-RPC request into a protocol-specific [Request].
 *
 * @return The decoded [Request] or null
 */
internal fun JSONRPCRequest.fromJSON(): Request? {
    val serializer = selectRequestDeserializer(method)
    val params = params
    return McpJson.decodeFromJsonElement<Request>(serializer, params)
}

/**
 * A custom request with a specified method.
 *
 * @param method The method associated with the request.
 */
@Serializable
public open class CustomRequest(override val method: Method) : Request

/**
 * Represents a notification in the protocol.
 */
@Serializable(with = NotificationPolymorphicSerializer::class)
public sealed interface Notification {
    public val method: Method
}

/**
 * Converts the notification to a JSON-RPC notification.
 *
 * @return The JSON-RPC notification representation.
 */
internal fun Notification.toJSON(): JSONRPCNotification {
    val encoded = McpJson.encodeToJsonElement<Notification>(this)
    return JSONRPCNotification(
        method.value,
        params = encoded
    )
}

/**
 * Decodes a JSON-RPC notification into a protocol-specific [Notification].
 *
 * @return The decoded [Notification].
 */
internal fun JSONRPCNotification.fromJSON(): Notification {
    return McpJson.decodeFromJsonElement<Notification>(params)
}

/**
 * Represents the result of a request, including additional metadata.
 */
@Serializable(with = RequestResultPolymorphicSerializer::class)
public sealed interface RequestResult : WithMeta

/**
 * An empty result for a request containing optional metadata.
 *
 * @param _meta Additional metadata for the response. Defaults to an empty JSON object.
 */
@Serializable
public data class EmptyRequestResult(
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult, ClientResult

/**
 * A uniquely identifying ID for a request in JSON-RPC.
 */
@Serializable(with = RequestIdSerializer::class)
public sealed interface RequestId {
    @Serializable
    public data class StringId(val value: String) : RequestId

    @Serializable
    public data class NumberId(val value: Long) : RequestId
}

/**
 * Represents a JSON-RPC message in the protocol.
 */
@Serializable(with = JSONRPCMessagePolymorphicSerializer::class)
public sealed interface JSONRPCMessage

/**
 * A request that expects a response.
 */
@OptIn(ExperimentalAtomicApi::class)
@Serializable
public data class JSONRPCRequest(
  val id: RequestId = RequestId.NumberId(REQUEST_MESSAGE_ID.incrementAndFetch()),
  val method: String,
  val params: JsonElement = EmptyJsonObject,
  val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCMessage

/**
 * A notification which does not expect a response.
 */
@Serializable
public data class JSONRPCNotification(
  val method: String,
  val params: JsonElement = EmptyJsonObject,
  val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCMessage

/**
 * A successful (non-error) response to a request.
 */
@Serializable
public class JSONRPCResponse(
    public val id: RequestId,
    public val jsonrpc: String = JSONRPC_VERSION,
    public val result: RequestResult? = null,
    public val error: JSONRPCError? = null,
) : JSONRPCMessage

/**
 * An incomplete set of error codes that may appear in JSON-RPC responses.
 */
@Serializable(with = ErrorCodeSerializer::class)
public sealed interface ErrorCode {
    public val code: Int

    @Serializable
    public enum class Defined(override val code: Int) : ErrorCode {
        // SDK error codes
        ConnectionClosed(-1),
        RequestTimeout(-2),

        // Standard JSON-RPC error codes
        ParseError(-32700),
        InvalidRequest(-32600),
        MethodNotFound(-32601),
        InvalidParams(-32602),
        InternalError(-32603),
        ;
    }

    @Serializable
    public data class Unknown(override val code: Int) : ErrorCode
}

/**
 * A response to a request that indicates an error occurred.
 */
@Serializable
public data class JSONRPCError(
  val code: ErrorCode,
  val message: String,
  val data: JsonObject = EmptyJsonObject,
) : JSONRPCMessage

/* Cancellation */
/**
 * This notification can be sent by either side to indicate that it is cancelling a previously issued request.
 *
 * The request SHOULD still be in-flight, but due to communication latency, it is always possible that this notification MAY arrive after the request has already finished.
 *
 * This notification indicates that the result will be unused, so any associated processing SHOULD cease.
 *
 * A client MUST NOT attempt to cancel its `initialize` request.
 */
@Serializable
public data class CancelledNotification(
  /**
     * The ID of the request to cancel.
     *
     * It MUST correspond to the ID of a request previously issued in the same direction.
     */
    val requestId: RequestId,
  /**
     * An optional string describing the reason for the cancellation. This MAY be logged or presented to the user.
     */
    val reason: String?,
  override val _meta: JsonObject = EmptyJsonObject,
) : ClientNotification, ServerNotification, WithMeta {
    override val method: Method = Method.Defined.NotificationsCancelled
}

/* Initialization */
/**
 * Describes the name and version of an MCP implementation.
 */
@Serializable
public data class Implementation(
    val name: String,
    val version: String,
)

/**
 * Capabilities a client may support.
 * Known capabilities are defined here, in this, but this is not a closed set:
 * any client can define its own, additional capabilities.
 */
@Serializable
public data class ClientCapabilities(
  /**
     * Experimental, non-standard capabilities that the client supports.
     */
    val experimental: JsonObject? = EmptyJsonObject,
  /**
     * Present if the client supports sampling from an LLM.
     */
    val sampling: JsonObject? = EmptyJsonObject,
  /**
     * Present if the client supports listing roots.
     */
    val roots: Roots? = null,
) {
    @Serializable
    public data class Roots(
        /**
         * Whether the client supports issuing notifications for changes to the root list.
         */
        val listChanged: Boolean?,
    )
}

/**
 * Represents a request sent by the client.
 */
//@Serializable(with = ClientRequestPolymorphicSerializer::class)
public interface ClientRequest : Request

/**
 * Represents a notification sent by the client.
 */
@Serializable(with = ClientNotificationPolymorphicSerializer::class)
public sealed interface ClientNotification : Notification

/**
 * Represents a result returned to the client.
 */
@Serializable(with = ClientResultPolymorphicSerializer::class)
public sealed interface ClientResult : RequestResult

/**
 * Represents a request sent by the server.
 */
//@Serializable(with = ServerRequestPolymorphicSerializer::class)
public sealed interface ServerRequest : Request

/**
 * Represents a notification sent by the server.
 */
@Serializable(with = ServerNotificationPolymorphicSerializer::class)
public sealed interface ServerNotification : Notification

/**
 * Represents a result returned by the server.
 */
@Serializable(with = ServerResultPolymorphicSerializer::class)
public sealed interface ServerResult : RequestResult

/**
 * Represents a request or notification for an unknown method.
 *
 * @param method The method that is unknown.
 */
@Serializable
public data class UnknownMethodRequestOrNotification(
    override val method: Method,
) : ClientNotification, ClientRequest, ServerNotification, ServerRequest

/**
 * This request is sent from the client to the server when it first connects, asking it to begin initialization.
 */
@Serializable
public data class InitializeRequest(
  val protocolVersion: String,
  val capabilities: ClientCapabilities,
  val clientInfo: Implementation,
  override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest, WithMeta {
    override val method: Method = Method.Defined.Initialize
}

/**
 * Represents the capabilities that a server can support.
 *
 * @property experimental Experimental, non-standard capabilities that the server supports.
 * @property sampling Present if the client supports sampling from an LLM.
 * @property logging Present if the server supports sending log messages to the client.
 * @property prompts Capabilities related to prompt templates offered by the server.
 * @property resources Capabilities related to resources available on the server.
 * @property tools Capabilities related to tools that can be called on the server.
 */
@Serializable
public data class ServerCapabilities(
  val experimental: JsonObject? = EmptyJsonObject,
  val sampling: JsonObject? = EmptyJsonObject,
  val logging: JsonObject? = EmptyJsonObject,
  val prompts: Prompts? = null,
  val resources: Resources? = null,
  val tools: Tools? = null,
) {
    /**
     * Capabilities related to prompt templates.
     *
     * @property listChanged Indicates if the server supports notifications when the prompt list changes.
     */
    @Serializable
    public data class Prompts(
        /**
         * Whether this server supports issuing notifications for changes to the prompt list.
         */
        val listChanged: Boolean?,
    )

    /**
     * Capabilities related to resources.
     *
     * @property subscribe Indicates if clients can subscribe to resource updates.
     * @property listChanged Indicates if the server supports notifications when the resource list changes.
     */
    @Serializable
    public data class Resources(
        /**
         * Whether this server supports clients subscribing to resource updates.
         */
        val subscribe: Boolean?,
        /**
         * Whether this server supports issuing notifications for changes to the resource list.
         */
        val listChanged: Boolean?,
    )

    /**
     * Capabilities related to tools.
     *
     * @property listChanged Indicates if the server supports notifications when the tool list changes.
     */
    @Serializable
    public data class Tools(
        /**
         * Whether this server supports issuing notifications for changes to the tool list.
         */
        val listChanged: Boolean?,
    )
}

/**
 * After receiving an initialized request from the client, the server sends this response.
 */
@Serializable
public data class InitializeResult(
  /**
     * The version of the Model Context Protocol that the server wants to use. This may not match the version that the client requested. If the client cannot support this version, it MUST disconnect.
     */
    val protocolVersion: String = LATEST_PROTOCOL_VERSION,
  val capabilities: ServerCapabilities = ServerCapabilities(),
  val serverInfo: Implementation,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult

/**
 * This notification is sent from the client to the server after initialization has finished.
 */
@Serializable
public class InitializedNotification : ClientNotification {
    override val method: Method = Method.Defined.NotificationsInitialized
}

/* Ping */
/**
 * A ping, issued by either the server or the client, to check that the other party is still alive.
 * The receiver must promptly respond, or else it may be disconnected.
 */
@Serializable
public class PingRequest : ServerRequest, ClientRequest {
    override val method: Method = Method.Defined.Ping
}

/**
 * Represents the base interface for progress tracking.
 */
@Serializable
public sealed interface ProgressBase {
    /**
     * The progress thus far. This should increase every time progress is made, even if the total is unknown.
     */
    public val progress: Int

    /**
     * Total number of items to a process (or total progress required), if known.
     */
    public val total: Double?

    /**
     * An optional message describing the current progress.
     */
    public val message: String?
}

/* Progress notifications */
/**
 * Represents a progress notification.
 *
 * @property progress The current progress value.
 * @property total The total progress required, if known.
 */
@Serializable
public open class Progress(
    /**
     * The progress thus far. This should increase every time progress is made, even if the total is unknown.
     */
    override val progress: Int,

    /**
     * Total number of items to a process (or total progress required), if known.
     */
    override val total: Double?,

    /**
     * An optional message describing the current progress.
     */
    override val message: String?,
) : ProgressBase

/**
 * An out-of-band notification used to inform the receiver of a progress update for a long-running request.
 */
@Serializable
public data class ProgressNotification(
  override val progress: Int,
  /**
     * The progress token,
     * which was given in the initial request,
     * used to associate this notification with the request that is proceeding.
     */
    public val progressToken: ProgressToken,
  @Suppress("PropertyName") val _meta: JsonObject = EmptyJsonObject,
  override val total: Double?,
  override val message: String?,
) : ClientNotification, ServerNotification, ProgressBase {
    override val method: Method = Method.Defined.NotificationsProgress
}

/* Pagination */
/**
 * Represents a request supporting pagination.
 */
@Serializable
public sealed interface PaginatedRequest : Request, WithMeta {
    /**
     * The cursor indicating the pagination position.
     */
    public val cursor: Cursor?
    override val _meta: JsonObject
}

/**
 * Represents a paginated result of a request.
 */
@Serializable
public sealed interface PaginatedResult : RequestResult {
    /**
     * An opaque token representing the pagination position after the last returned result.
     * If present, there may be more results available.
     */
    public val nextCursor: Cursor?
}

/* Resources */
/**
 * The contents of a specific resource or sub-resource.
 */
@Serializable(with = ResourceContentsPolymorphicSerializer::class)
public sealed interface ResourceContents {
    /**
     * The URI of this resource.
     */
    public val uri: String

    /**
     * The MIME type of this resource, if known.
     */
    public val mimeType: String?
}

/**
 * Represents the text contents of a resource.
 *
 * @property text The text of the item. This must only be set if the item can actually be represented as text (not binary data).
 */
@Serializable
public data class TextResourceContents(
    val text: String,
    override val uri: String,
    override val mimeType: String?,
) : ResourceContents

/**
 * Represents the binary contents of a resource encoded as a base64 string.
 *
 * @property blob A base64-encoded string representing the binary data of the item.
 */
@Serializable
public data class BlobResourceContents(
    val blob: String,
    override val uri: String,
    override val mimeType: String?,
) : ResourceContents

/**
 * Represents resource contents with unknown or unspecified data.
 */
@Serializable
public data class UnknownResourceContents(
    override val uri: String,
    override val mimeType: String?,
) : ResourceContents

/**
 * A known resource that the server is capable of reading.
 */
@Serializable
public data class Resource(
    /**
     * The URI of this resource.
     */
    val uri: String,
    /**
     * A human-readable name for this resource.
     *
     * Clients can use this to populate UI elements.
     */
    val name: String,
    /**
     * A description of what this resource represents.
     *
     * Clients can use this to improve the LLM's understanding of available resources.
     * It can be thought of as a "hint" to the model.
     */
    val description: String?,
    /**
     * The MIME type of this resource, if known.
     */
    val mimeType: String?,
)

/**
 * A template description for resources available on the server.
 */
@Serializable
public data class ResourceTemplate(
    /**
     * A URI template (according to RFC 6570) that can be used to construct resource URIs.
     */
    val uriTemplate: String,
    /**
     * A human-readable name for the type of resource this template refers to.
     *
     * Clients can use this to populate UI elements.
     */
    val name: String,
    /**
     * A description of what this template is for.
     *
     * Clients can use this to improve the LLM's understanding of available resources.
     * It can be thought of as a "hint" to the model.
     */
    val description: String?,
    /**
     * The MIME type for all resources that match this template. This should only be included if all resources matching this template have the same type.
     */
    val mimeType: String?,
)

/**
 * Sent from the client to request a list of resources the server has.
 */
@Serializable
public data class ListResourcesRequest(
    override val cursor: Cursor? = null,
    override val _meta: JsonObject = EmptyJsonObject
) : ClientRequest, PaginatedRequest {
    override val method: Method = Method.Defined.ResourcesList
}

/**
 * The server's response to a resources/list request from the client.
 */
@Serializable
public class ListResourcesResult(
  public val resources: List<Resource>,
  override val nextCursor: Cursor? = null,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult, PaginatedResult

/**
 * Sent from the client to request a list of resource templates the server has.
 */
@Serializable
public data class ListResourceTemplatesRequest(
    override val cursor: Cursor?,
    override val _meta: JsonObject = EmptyJsonObject
) : ClientRequest, PaginatedRequest {
    override val method: Method = Method.Defined.ResourcesTemplatesList
}

/**
 * The server's response to a resources/templates/list request from the client.
 */
@Serializable
public class ListResourceTemplatesResult(
  public val resourceTemplates: List<ResourceTemplate>,
  override val nextCursor: Cursor? = null,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult, PaginatedResult

/**
 * Sent from the client to the server to read a specific resource URI.
 */
@Serializable
public data class ReadResourceRequest(
  val uri: String,
  override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest, WithMeta {
    override val method: Method = Method.Defined.ResourcesRead
}

/**
 * The server's response to a resources/read request from the client.
 */
@Serializable
public class ReadResourceResult(
  public val contents: List<ResourceContents>,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult

/**
 * An optional notification from the server to the client,
 * informing it that the list of resources it can read from has changed.
 * Servers may issue this without any previous subscription from the client.
 */
@Serializable
public class ResourceListChangedNotification : ServerNotification {
    override val method: Method = Method.Defined.NotificationsResourcesListChanged
}

/**
 * Sent from the client to request resources/updated notifications from the server whenever a particular resource changes.
 */
@Serializable
public data class SubscribeRequest(
  /**
     * The URI of the resource to subscribe to. The URI can use any protocol; it is up to the server how to interpret it.
     */
    val uri: String,
  override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest, WithMeta {
    override val method: Method = Method.Defined.ResourcesSubscribe
}

/**
 * Sent from the client to request cancellation of resources/updated notifications from the server. This should follow a previous resources/subscribe request.
 */
@Serializable
public data class UnsubscribeRequest(
  /**
     * The URI of the resource to unsubscribe from.
     */
    val uri: String,
  override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest, WithMeta {
    override val method: Method = Method.Defined.ResourcesUnsubscribe
}

/**
 * A notification from the server to the client, informing it that a resource has changed and may need to be read again. This should only be sent if the client previously sent a resources/subscribe request.
 */
@Serializable
public data class ResourceUpdatedNotification(
  /**
     * The URI of the resource that has been updated. This might be a sub-resource of the one that the client actually subscribed to.
     */
    val uri: String,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerNotification, WithMeta {
    override val method: Method = Method.Defined.NotificationsResourcesUpdated
}

/* Prompts */
/**
 * Describes an argument that a prompt can accept.
 */
@Serializable
public data class PromptArgument(
    /**
     * The name of the argument.
     */
    val name: String,
    /**
     * A human-readable description of the argument.
     */
    val description: String?,
    /**
     * Whether this argument must be provided.
     */
    val required: Boolean?,
)

/**
 * A prompt or prompt template that the server offers.
 */
@Serializable
public class Prompt(
    /**
     * The name of the prompt or prompt template.
     */
    public val name: String,
    /**
     * An optional description of what this prompt provides
     */
    public val description: String?,
    /**
     * A list of arguments to use for templating the prompt.
     */
    public val arguments: List<PromptArgument>?,
)

/**
 * Sent from the client to request a list of prompts and prompt templates the server has.
 */
@Serializable
public data class ListPromptsRequest(
    override val cursor: Cursor? = null,
    override val _meta: JsonObject = EmptyJsonObject
) : ClientRequest, PaginatedRequest {
    override val method: Method = Method.Defined.PromptsList
}

/**
 * The server's response to a prompts/list request from the client.
 */
@Serializable
public class ListPromptsResult(
  public val prompts: List<Prompt>,
  override val nextCursor: Cursor? = null,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult, PaginatedResult

/**
 * Used by the client to get a prompt provided by the server.
 */
@Serializable
public data class GetPromptRequest(
  /**
     * The name of the prompt or prompt template.
     */
    val name: String,

  /**
     * Arguments to use for templating the prompt.
     */
    val arguments: Map<String, String>?,

  override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest, WithMeta {
    override val method: Method = Method.Defined.PromptsGet
}

/**
 * Represents the content of a prompt message.
 */
@Serializable(with = PromptMessageContentPolymorphicSerializer::class)
public sealed interface PromptMessageContent {
    public val type: String
}

/**
 * Represents prompt message content that is either text, image or audio.
 */
@Serializable(with = PromptMessageContentMultimodalPolymorphicSerializer::class)
public sealed interface PromptMessageContentMultimodal : PromptMessageContent

/**
 * Text provided to or from an LLM.
 */
@Serializable
public data class TextContent(
    /**
     * The text content of the message.
     */
    val text: String? = null,
) : PromptMessageContentMultimodal {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "text"
    }
}

/**
 * An image provided to or from an LLM.
 */
@Serializable
public data class ImageContent(
    /**
     * The base64-encoded image data.
     */
    val data: String,

    /**
     * The MIME type of the image. Different providers may support different image types.
     */
    val mimeType: String,
) : PromptMessageContentMultimodal {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "image"
    }
}

/**
 * Audio provided to or from an LLM.
 */
@Serializable
public data class AudioContent(
    /**
     * The base64-encoded audio data.
     */
    val data: String,

    /**
     * The MIME type of the audio. Different providers may support different audio types.
     */
    val mimeType: String,
) : PromptMessageContentMultimodal {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "audio"
    }
}


/**
 * Unknown content provided to or from an LLM.
 */
@Serializable
public data class UnknownContent(
    override val type: String,
) : PromptMessageContentMultimodal

/**
 * The contents of a resource, embedded into a prompt or tool call result.
 */
@Serializable
public data class EmbeddedResource(
    val resource: ResourceContents,
) : PromptMessageContent {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "resource"
    }
}

/**
 * Enum representing the role of a participant.
 */
@Suppress("EnumEntryName")
@Serializable
public enum class Role {
    user, assistant,
}

/**
 * Describes a message returned as part of a prompt.
 */
@Serializable
public data class PromptMessage(
    val role: Role,
    val content: PromptMessageContent,
)

/**
 * The server's response to a prompts/get request from the client.
 */
@Serializable
public class GetPromptResult(
  /**
     * An optional description for the prompt.
     */
    public val description: String?,
  public val messages: List<PromptMessage>,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult

/**
 * An optional notification from the server to the client, informing it that the list of prompts it offers has changed.
 * Servers may issue this without any previous subscription from the client.
 */
@Serializable
public class PromptListChangedNotification : ServerNotification {
    override val method: Method = Method.Defined.NotificationsPromptsListChanged
}

/* Tools */
/**
 * Definition for a tool the client can call.
 */
@Serializable
public data class Tool(
    /**
     * The name of the tool.
     */
    val name: String,
    /**
     * A human-readable description of the tool.
     */
    val description: String?,
    /**
     * A JSON object defining the expected parameters for the tool.
     */
    val inputSchema: Input,
) {
    @Serializable
    public data class Input(
      val properties: JsonObject = EmptyJsonObject,
      val required: List<String>? = null,
    ) {
        @OptIn(ExperimentalSerializationApi::class)
        @EncodeDefault
        val type: String = "object"
    }
}

/**
 * Sent from the client to request a list of tools the server has.
 */
@Serializable
public data class ListToolsRequest(
    override val cursor: Cursor? = null,
    override val _meta: JsonObject = EmptyJsonObject
) : ClientRequest, PaginatedRequest {
    override val method: Method = Method.Defined.ToolsList
}

/**
 * The server's response to a tools/list request from the client.
 */
@Serializable
public class ListToolsResult(
  public val tools: List<Tool>,
  override val nextCursor: Cursor?,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult, PaginatedResult

/**
 * The server's response to a tool call.
 */
@Serializable
public sealed interface CallToolResultBase : ServerResult {
    public val content: List<PromptMessageContent>
    public val isError: Boolean? get() = false
}

/**
 * The server's response to a tool call.
 */
@Serializable
public class CallToolResult(
  override val content: List<PromptMessageContent>,
  override val isError: Boolean? = false,
  override val _meta: JsonObject = EmptyJsonObject,
) : CallToolResultBase

/**
 * [CallToolResult] extended with backwards compatibility to protocol version 2024-10-07.
 */
@Serializable
public class CompatibilityCallToolResult(
  override val content: List<PromptMessageContent>,
  override val isError: Boolean? = false,
  override val _meta: JsonObject = EmptyJsonObject,
  public val toolResult: JsonObject = EmptyJsonObject,
) : CallToolResultBase

/**
 * Used by the client to invoke a tool provided by the server.
 */
@Serializable
public data class CallToolRequest(
  val name: String,
  val arguments: JsonObject = EmptyJsonObject,
  override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest, WithMeta {
    override val method: Method = Method.Defined.ToolsCall
}

/**
 * An optional notification from the server to the client, informing it that the list of tools it offers has changed.
 * Servers may issue this without any previous subscription from the client.
 */
@Serializable
public class ToolListChangedNotification : ServerNotification {
    override val method: Method = Method.Defined.NotificationsToolsListChanged
}

/* Logging */
/**
 * The severity of a log message.
 */
@Suppress("EnumEntryName")
@Serializable
public enum class LoggingLevel {
    debug,
    info,
    notice,
    warning,
    error,
    critical,
    alert,
    emergency,
    ;
}

/**
 * Notification of a log message passed from server to client.
 * If no logging level request has been sent from the client,
 * the server MAY decide which messages to send automatically.
 */
@Serializable
public data class LoggingMessageNotification(
  /**
     * The severity of this log message.
     */
    val level: LoggingLevel,

  /**
     * An optional name of the logger issuing this message.
     */
    val logger: String? = null,

  /**
     * The data to be logged, such as a string message or an object. Any JSON serializable type is allowed here.
     */
    val data: JsonObject = EmptyJsonObject,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerNotification, WithMeta {
    /**
     * A request from the client to the server to enable or adjust logging.
     */
    @Serializable
    public data class SetLevelRequest(
      /**
         * The level of logging that the client wants to receive from the server. The server should send all logs at this level and higher (i.e., more severe) to the client as notifications/logging/message.
         */
        val level: LoggingLevel,
      override val _meta: JsonObject = EmptyJsonObject,
    ) : ClientRequest, WithMeta {
        override val method: Method = Method.Defined.LoggingSetLevel
    }

    override val method: Method = Method.Defined.NotificationsMessage
}

/* Sampling */
/**
 * Hints to use for model selection.
 */
@Serializable
public data class ModelHint(
    /**
     * A hint for a model name.
     */
    val name: String?,
)

/**
 * The server's preferences for model selection, requested by the client during sampling.
 */
@Suppress("CanBeParameter")
@Serializable
public class ModelPreferences(
    /**
     * Optional hints to use for model selection.
     */
    public val hints: List<ModelHint>?,
    /**
     * How much to prioritize cost when selecting a model.
     */
    public val costPriority: Double?,
    /**
     * How much to prioritize sampling speed (latency) when selecting a model.
     */
    public val speedPriority: Double?,
    /**
     * How much to prioritize intelligence and capabilities when selecting a model.
     */
    public val intelligencePriority: Double?,
) {
    init {
        require(costPriority == null || costPriority in 0.0..1.0) {
            "costPriority must be in 0.0 <= x <= 1.0 value range"
        }

        require(speedPriority == null || speedPriority in 0.0..1.0) {
            "costPriority must be in 0.0 <= x <= 1.0 value range"
        }

        require(intelligencePriority == null || intelligencePriority in 0.0..1.0) {
            "intelligencePriority must be in 0.0 <= x <= 1.0 value range"
        }
    }
}

/**
 * Describes a message issued to or received from an LLM API.
 */
@Serializable
public data class SamplingMessage(
    val role: Role,
    val content: PromptMessageContentMultimodal,
)

/**
 * A request from the server to sample an LLM via the client.
 * The client has full discretion over which model to select.
 * The client should also inform the user before beginning sampling to allow them to inspect the request
 * (human in the loop) and decide whether to approve it.
 */
@Serializable
public data class CreateMessageRequest(
  val messages: List<SamplingMessage>,
  /**
     * An optional system prompt the server wants to use it for sampling. The client MAY modify or omit this prompt.
     */
    val systemPrompt: String?,
  /**
     * A request to include context from one or more MCP servers (including the caller), to be attached to the prompt. The client MAY ignore this request.
     */
    val includeContext: IncludeContext?,
  val temperature: Double?,
  /**
     * The maximum number of tokens to sample, as requested by the server. The client MAY choose to sample fewer tokens than requested.
     */
    val maxTokens: Int,
  val stopSequences: List<String>?,
  /**
     * Optional metadata to pass through to the LLM provider. The format of this metadata is provider-specific.
     */
    val metadata: JsonObject = EmptyJsonObject,
  /**
     * The server's preferences for which model to select.
     */
    val modelPreferences: ModelPreferences?,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerRequest, WithMeta {
    override val method: Method = Method.Defined.SamplingCreateMessage

    @Serializable
    public enum class IncludeContext { none, thisServer, allServers }
}

@Serializable(with = StopReasonSerializer::class)
public sealed interface StopReason {
    public val value: String

    @Serializable
    public data object EndTurn : StopReason {
        override val value: String = "endTurn"
    }

    @Serializable
    public data object StopSequence : StopReason {
        override val value: String = "stopSequence"
    }

    @Serializable
    public data object MaxTokens : StopReason {
        override val value: String = "maxTokens"
    }

    @Serializable
    @JvmInline
    public value class Other(override val value: String) : StopReason
}

/**
 * The client's response to a sampling/create_message request from the server.
 * The client should inform the user before returning the sampled message to allow them to inspect the response
 * (human in the loop) and decide whether to allow the server to see it.
 */
@Serializable
public data class CreateMessageResult(
  /**
     * The name of the model that generated the message.
     */
    val model: String,
  /**
     * The reason why sampling stopped.
     */
    val stopReason: StopReason? = null,
  val role: Role,
  val content: PromptMessageContentMultimodal,
  override val _meta: JsonObject = EmptyJsonObject,
) : ClientResult

/* Autocomplete */
@Serializable(with = ReferencePolymorphicSerializer::class)
public sealed interface Reference {
    public val type: String
}

/**
 * A reference to a resource or resource template definition.
 */
@Serializable
public data class ResourceReference(
    /**
     * The URI or URI template of the resource.
     */
    val uri: String,
) : Reference {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "ref/resource"
    }
}

/**
 * Identifies a prompt.
 */
@Serializable
public data class PromptReference(
    /**
     * The name of the prompt or prompt template
     */
    val name: String,
) : Reference {
    override val type: String = TYPE

    public companion object {
        public const val TYPE: String = "ref/prompt"
    }
}

/**
 * Identifies a prompt.
 */
@Serializable
public data class UnknownReference(
    override val type: String,
) : Reference

/**
 * A request from the client to the server to ask for completion options.
 */
@Serializable
public data class CompleteRequest(
  val ref: Reference,
  /**
     * The argument's information
     */
    val argument: Argument,
  override val _meta: JsonObject = EmptyJsonObject,
) : ClientRequest, WithMeta {
    override val method: Method = Method.Defined.CompletionComplete

    @Serializable
    public data class Argument(
        /**
         * The name of the argument
         */
        val name: String,
        /**
         * The value of the argument to use for completion matching.
         */
        val value: String,
    )
}

/**
 * The server's response to a completion/complete request
 */
@Serializable
public data class CompleteResult(
  val completion: Completion,
  override val _meta: JsonObject = EmptyJsonObject,
) : ServerResult {
    @Suppress("CanBeParameter")
    @Serializable
    public class Completion(
        /**
         * A list of completion values. Must not exceed 100 items.
         */
        public val values: List<String>,
        /**
         * The total number of completion options available. This can exceed the number of values actually sent in the response.
         */
        public val total: Int?,
        /**
         * Indicates whether there are additional completion options beyond those provided in the current response, even if the exact total is unknown.
         */
        public val hasMore: Boolean?,
    ) {
        init {
            require(values.size <= 100) {
                "'values' field must not exceed 100 items"
            }
        }
    }
}

/* Roots */
/**
 * Represents a root directory or file that the server can operate on.
 */
@Serializable
public data class Root(
    /**
     * The URI identifying the root. This *must* start with file:// for now.
     */
    val uri: String,

    /**
     * An optional name for the root.
     */
    val name: String?,
) {
    init {
        require(uri.startsWith("file://")) {
            "'uri' param must start with 'file://'"
        }
    }
}

/**
 * Sent from the server to request a list of root URIs from the client.
 */
@Serializable
public class ListRootsRequest(override val _meta: JsonObject = EmptyJsonObject) : ServerRequest, WithMeta {
    override val method: Method = Method.Defined.RootsList
}

/**
 * The client's response to a roots/list request from the server.
 */
@Serializable
public class ListRootsResult(
  public val roots: List<Root>,
  override val _meta: JsonObject = EmptyJsonObject,
) : ClientResult

/**
 * A notification from the client to the server, informing it that the list of roots has changed.
 */
@Serializable
public class RootsListChangedNotification : ClientNotification {
    override val method: Method = Method.Defined.NotificationsRootsListChanged
}

/**
 * Represents an error specific to the MCP protocol.
 *
 * @property code The error code.
 * @property message The error message.
 * @property data Additional error data as a JSON object.
 */
public class McpError(public val code: Int, message: String, public val data: JsonObject = EmptyJsonObject) :
    Exception() {
    override val message: String = "MCP error ${code}: $message"
}
