package com.intellij.mcpserver.stdio.mcpProto.shared

import com.intellij.mcpserver.stdio.mcpProto.KotlinLogging
import com.intellij.mcpserver.stdio.mcpProto.CancelledNotification
import com.intellij.mcpserver.stdio.mcpProto.EmptyRequestResult
import com.intellij.mcpserver.stdio.mcpProto.ErrorCode
import com.intellij.mcpserver.stdio.mcpProto.JSONRPCError
import com.intellij.mcpserver.stdio.mcpProto.JSONRPCNotification
import com.intellij.mcpserver.stdio.mcpProto.JSONRPCRequest
import com.intellij.mcpserver.stdio.mcpProto.JSONRPCResponse
import com.intellij.mcpserver.stdio.mcpProto.McpError
import com.intellij.mcpserver.stdio.mcpProto.Method
import com.intellij.mcpserver.stdio.mcpProto.Notification
import com.intellij.mcpserver.stdio.mcpProto.PingRequest
import com.intellij.mcpserver.stdio.mcpProto.Progress
import com.intellij.mcpserver.stdio.mcpProto.ProgressNotification
import com.intellij.mcpserver.stdio.mcpProto.Request
import com.intellij.mcpserver.stdio.mcpProto.RequestId
import com.intellij.mcpserver.stdio.mcpProto.RequestResult
import com.intellij.mcpserver.stdio.mcpProto.fromJSON
import com.intellij.mcpserver.stdio.mcpProto.toJSON
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import kotlin.collections.get
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val LOGGER = KotlinLogging.logger { }

internal const val IMPLEMENTATION_NAME = "mcp-ktor"

/**
 * Callback for progress notifications.
 */
public typealias ProgressCallback = (Progress) -> Unit

@OptIn(ExperimentalSerializationApi::class)
public val McpJson: Json by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
        explicitNulls = false
    }
}

/**
 * Additional initialization options.
 */
public open class ProtocolOptions(
    /**
     * Whether to restrict emitted requests to only those that the remote side has indicated
     * that they can handle, through their advertised capabilities.
     *
     * Note that this DOES NOT affect checking of _local_ side capabilities, as it is
     * considered a logic error to mis-specify those.
     *
     * Currently, this defaults to false, for backwards compatibility with SDK versions
     * that did not advertise capabilities correctly.
     * In the future, this will default to true.
     */
    public var enforceStrictCapabilities: Boolean = false,

    public var timeout: Duration = DEFAULT_REQUEST_TIMEOUT,
)

/**
 * The default request timeout.
 */
public val DEFAULT_REQUEST_TIMEOUT: Duration = 60000.milliseconds

/**
 * Options that can be given per request.
 */
public data class RequestOptions(
    /**
     * If set, requests progress notifications from the remote end (if supported).
     * When progress notifications are received, this callback will be invoked.
     */
    val onProgress: ProgressCallback? = null,

    /**
     * A timeout for this request. If exceeded, an McpError with code `RequestTimeout`
     * will be raised from request().
     *
     * If not specified, `DEFAULT_REQUEST_TIMEOUT` will be used as the timeout.
     */
    val timeout: Duration = DEFAULT_REQUEST_TIMEOUT,
)

/**
 * Extra data given to request handlers.
 */
public class RequestHandlerExtra()

internal val COMPLETED = CompletableDeferred(Unit).also { it.complete(Unit) }

/**
 * Implements MCP protocol framing on top of a pluggable transport, including
 * features like request/response linking, notifications, and progress.
 */
public abstract class Protocol(
    @PublishedApi internal val options: ProtocolOptions?,
) {
    public var transport: Transport? = null
        private set

    @PublishedApi
    internal val requestHandlers: MutableMap<String, suspend (request: JSONRPCRequest, extra: RequestHandlerExtra) -> RequestResult?> =
        mutableMapOf()
    public val notificationHandlers: MutableMap<String, suspend (notification: JSONRPCNotification) -> Unit> =
        mutableMapOf()

    @PublishedApi
    internal val responseHandlers: MutableMap<RequestId, (response: JSONRPCResponse?, error: Exception?) -> Unit> =
        mutableMapOf()

    @PublishedApi
    internal val progressHandlers: MutableMap<RequestId, ProgressCallback> = mutableMapOf()

    /**
     * Callback for when the connection is closed for any reason.
     *
     * This is invoked when close() is called as well.
     */
    public open fun onClose() {}

    /**
     * Callback for when an error occurs.
     *
     * Note that errors are not necessarily fatal they are used
     * for reporting any kind of exceptional condition out of a band.
     */
    public open fun onError(error: Throwable) {}

    /**
     * A handler to invoke for any request types that do not have their own handler installed.
     */
    public var fallbackRequestHandler: (suspend (request: JSONRPCRequest, extra: RequestHandlerExtra) -> RequestResult?)? =
        null

    /**
     * A handler to invoke for any notification types that do not have their own handler installed.
     */
    public var fallbackNotificationHandler: (suspend (notification: JSONRPCNotification) -> Unit)? = null

    init {
        setNotificationHandler<ProgressNotification>(Method.Defined.NotificationsProgress) { notification ->
            onProgress(notification)
            COMPLETED
        }

        setRequestHandler<PingRequest>(Method.Defined.Ping) { request, _ ->
            EmptyRequestResult()
        }
    }

    /**
     * Attaches to the given transport, starts it, and starts listening for messages.
     *
     * The Protocol object assumes ownership of the Transport, replacing any callbacks that have already been set, and expects that it is the only user of the Transport instance going forward.
     */
    public open suspend fun connect(transport: Transport) {
        this.transport = transport
        transport.onClose {
            doClose()
        }

        transport.onError {
            onError(it)
        }

        transport.onMessage { message ->
            when (message) {
                is JSONRPCResponse -> onResponse(message, null)
                is JSONRPCRequest -> onRequest(message)
                is JSONRPCNotification -> onNotification(message)
                is JSONRPCError -> onResponse(null, message)
            }
        }

        return transport.start()
    }

    private fun doClose() {
        responseHandlers.clear()
        progressHandlers.clear()
        transport = null
        onClose()

        val error = McpError(ErrorCode.Defined.ConnectionClosed.code, "Connection closed")
        for (handler in responseHandlers.values) {
            handler(null, error)
        }
    }

    private suspend fun onNotification(notification: JSONRPCNotification) {
        LOGGER.trace { "Received notification: ${notification.method}" }
        val function = notificationHandlers[notification.method]
        val property = fallbackNotificationHandler
        val handler = function ?: property

        if (handler == null) {
            LOGGER.trace { "No handler found for notification: ${notification.method}" }
            return
        }
        try {
            handler(notification)
        } catch (cause: Throwable) {
            LOGGER.error(cause) { "Error handling notification: ${notification.method}" }
            onError(cause)
        }
    }

    private suspend fun onRequest(request: JSONRPCRequest) {
        LOGGER.trace { "Received request: ${request.method} (id: ${request.id})" }
        val handler = requestHandlers[request.method] ?: fallbackRequestHandler

        if (handler === null) {
            LOGGER.trace { "No handler found for request: ${request.method}" }
            try {
                transport?.send(
                    JSONRPCResponse(
                        id = request.id,
                        error = JSONRPCError(
                            ErrorCode.Defined.MethodNotFound,
                            message = "Server does not support ${request.method}",
                        )
                    )
                )
            } catch (cause: Throwable) {
                LOGGER.error(cause) { "Error sending method not found response" }
                onError(cause)
            }
            return
        }

        try {
            val result = handler(request, RequestHandlerExtra())
            LOGGER.trace { "Request handled successfully: ${request.method} (id: ${request.id})" }

            transport?.send(
                JSONRPCResponse(
                    id = request.id,
                    result = result
                )
            )

        } catch (cause: Throwable) {
            LOGGER.error(cause) { "Error handling request: ${request.method} (id: ${request.id})" }

            try {
                transport?.send(
                    JSONRPCResponse(
                        id = request.id,
                        error = JSONRPCError(
                            code = ErrorCode.Defined.InternalError,
                            message = cause.message ?: "Internal error"
                        )
                    )
                )
            } catch (sendError: Throwable) {
                LOGGER.error(sendError) { "Failed to send error response for request: ${request.method} (id: ${request.id})" }
                // Optionally implement fallback behavior here
            }
        }
    }

    private fun onProgress(notification: ProgressNotification) {
        LOGGER.trace { "Received progress notification: token=${notification.progressToken}, progress=${notification.progress}/${notification.total}" }
        val progress = notification.progress
        val total = notification.total
        val message = notification.message
        val progressToken = notification.progressToken

        val handler = progressHandlers[progressToken]
        if (handler == null) {
            val error = Error(
                "Received a progress notification for an unknown token: ${McpJson.encodeToString(notification)}",
            )
            LOGGER.error { error.message }
            onError(error)
            return
        }

        handler.invoke(Progress(progress, total, message))
    }

    private fun onResponse(response: JSONRPCResponse?, error: JSONRPCError?) {
        val messageId = response?.id
        val handler = responseHandlers[messageId]
        if (handler == null) {
            onError(Error("Received a response for an unknown message ID: ${McpJson.encodeToString(response)}"))
            return
        }

        responseHandlers.remove(messageId)
        progressHandlers.remove(messageId)
        if (response != null) {
            handler(response, null)
        } else {
            check(error != null)
            val error = McpError(
                error.code.code,
                error.message,
                error.data,
            )

            handler(null, error)
        }
    }

    /**
     * Closes the connection.
     */
    public suspend fun close() {
        transport?.close()
    }

    /**
     * A method to check if a capability is supported by the remote side, for the given method to be called.
     *
     * This should be implemented by subclasses.
     */
    protected abstract fun assertCapabilityForMethod(method: Method)

    /**
     * A method to check if a notification is supported by the local side, for the given method to be sent.
     *
     * This should be implemented by subclasses.
     */
    protected abstract fun assertNotificationCapability(method: Method)

    /**
     * A method to check if a request handler is supported by the local side, for the given method to be handled.
     *
     * This should be implemented by subclasses.
     */
    public abstract fun assertRequestHandlerCapability(method: Method)

    /**
     * Sends a request and waits for a response.
     *
     * Do not use this method to emit notifications! Use notification() instead.
     */
    public suspend fun <T : RequestResult> request(
        request: Request,
        options: RequestOptions? = null,
    ): T {
        LOGGER.trace { "Sending request: ${request.method}" }
        val result = CompletableDeferred<T>()
        val transport = this@Protocol.transport ?: throw Error("Not connected")

        if (this@Protocol.options?.enforceStrictCapabilities == true) {
            assertCapabilityForMethod(request.method)
        }

        val message = request.toJSON()
        val messageId = message.id

        if (options?.onProgress != null) {
            LOGGER.trace { "Registering progress handler for request id: $messageId" }
            progressHandlers[messageId] = options.onProgress
        }

        responseHandlers[messageId] = set@{ response, error ->
            if (error != null) {
                result.completeExceptionally(error)
                return@set
            }

            if (response?.error != null) {
                result.completeExceptionally(IllegalStateException(response.error.toString()))
                return@set
            }

            try {
                @Suppress("UNCHECKED_CAST")
                result.complete(response!!.result as T)
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }

        val cancel: suspend (Throwable) -> Unit = { reason: Throwable ->
            responseHandlers.remove(messageId)
            progressHandlers.remove(messageId)

            val notification = CancelledNotification(requestId = messageId, reason = reason.message ?: "Unknown")

            val serialized = JSONRPCNotification(
                notification.method.value,
                params = McpJson.encodeToJsonElement(notification)
            )
            transport.send(serialized)

            result.completeExceptionally(reason)
            Unit
        }

        val timeout = options?.timeout ?: DEFAULT_REQUEST_TIMEOUT
        try {
            withTimeout(timeout) {
                LOGGER.trace { "Sending request message with id: $messageId" }
                this@Protocol.transport?.send(message)
            }
            return result.await()
        } catch (cause: TimeoutCancellationException) {
            LOGGER.error { "Request timed out after ${timeout.inWholeMilliseconds}ms: ${request.method}" }
            cancel(
                McpError(
                    ErrorCode.Defined.RequestTimeout.code,
                    "Request timed out",
                    JsonObject(mutableMapOf("timeout" to JsonPrimitive(timeout.inWholeMilliseconds)))
                ),
            )
            result.cancel(cause)
            throw cause
        }
    }

    /**
     * Emits a notification, which is a one-way message that does not expect a response.
     */
    public suspend fun notification(notification: Notification) {
        LOGGER.trace { "Sending notification: ${notification.method}" }
        val transport = this.transport ?: error("Not connected")
        assertNotificationCapability(notification.method)

        val message = JSONRPCNotification(
            notification.method.value,
            params = McpJson.encodeToJsonElement<Notification>(notification) as JsonObject,
        )
        transport.send(message)
    }

    /**
     * Registers a handler to invoke when this protocol object receives a request with the given method.
     *
     * Note that this will replace any previous request handler for the same method.
     */
    public inline fun <reified T : Request> setRequestHandler(
        method: Method,
        noinline block: suspend (T, RequestHandlerExtra) -> RequestResult?,
    ) {
        setRequestHandler(typeOf<T>(), method, block)
    }

    @PublishedApi
    internal fun <T : Request> setRequestHandler(
        requestType: KType,
        method: Method,
        block: suspend (T, RequestHandlerExtra) -> RequestResult?,
    ) {
        assertRequestHandlerCapability(method)

        val serializer = McpJson.serializersModule.serializer(requestType)

        requestHandlers[method.value] = { request, extraHandler ->
            val result = McpJson.decodeFromJsonElement(serializer, request.params)
            val response = if (result != null) {
                @Suppress("UNCHECKED_CAST")
                block(result as T, extraHandler)
            } else {
                EmptyRequestResult()
            }

            response
        }
    }

    /**
     * Removes the request handler for the given method.
     */
    public fun removeRequestHandler(method: Method) {
        requestHandlers.remove(method.value)
    }

    /**
     * Registers a handler to invoke when this protocol object receives a notification with the given method.
     *
     * Note that this will replace any previous notification handler for the same method.
     */
    public fun <T : Notification> setNotificationHandler(method: Method, handler: (notification: T) -> Deferred<Unit>) {
        notificationHandlers[method.value] = {
            @Suppress("UNCHECKED_CAST")
            handler(it.fromJSON() as T)
        }
    }

    /**
     * Removes the notification handler for the given method.
     */
    public fun removeNotificationHandler(method: Method) {
        notificationHandlers.remove(method.value)
    }
}
