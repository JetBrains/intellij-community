package com.intellij.mcpserver.impl.util.network

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.modelcontextprotocol.kotlin.sdk.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.JSONRPCError
import io.modelcontextprotocol.kotlin.sdk.ErrorCode
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Simplified server transport for MCP Streamable HTTP connections.
 *
 * This implementation supports:
 *  * Session negotiation with a generated `mcp-session-id`
 *  * Request handling over POST with JSON responses
 *  * Notifications and server-initiated messages over a dedicated GET SSE stream
 *  * Session termination via DELETE
 *
 * The implementation intentionally omits resumability features for now. Messages emitted while no SSE consumer
 * is connected will be dropped, matching the current behaviour of other transports.
 */
internal class StreamableHttpServerTransport : AbstractTransport() {

  val sessionId: String = UUID.randomUUID().toString()

  private val started = AtomicBoolean(false)
  private val initialized = AtomicBoolean(false)
  private val closing = AtomicBoolean(false)
  private val hasActiveSse = AtomicBoolean(false)

  private val pendingRequests = ConcurrentHashMap<RequestId, CompletableDeferred<JSONRPCMessage>>()
  private val sseEvents = Channel<String>(capacity = Channel.UNLIMITED)

  private var negotiatedProtocolVersion: String = LATEST_PROTOCOL_VERSION

  override suspend fun start() {
    if (!started.compareAndSet(false, true)) {
      error("Streamable HTTP transport already started")
    }
  }

  override suspend fun send(message: JSONRPCMessage) {
    if (!started.get()) {
      error("Transport not started")
    }
    if (closing.get()) return

    when (message) {
      is JSONRPCResponse, is JSONRPCError -> {
        val requestId = when (message) {
          is JSONRPCResponse -> message.id
          is JSONRPCError -> {
            // JSONRPCError does not have an id; rely on a related request map
            null
          }
          else -> null
        }
        val deferred = requestId?.let { pendingRequests[it] }
        if (deferred != null && !deferred.isCompleted) {
          deferred.complete(message)
          if (message is JSONRPCResponse) {
            extractProtocolVersion(message)?.let { negotiatedProtocolVersion = it }
          }
        }
        else {
          // No matching request – treat as server initiated message
          emitToSse(message)
        }
      }
      else -> emitToSse(message)
    }
  }

  override suspend fun close() {
    if (!closing.compareAndSet(false, true)) return

    pendingRequests.forEach { (_, deferred) ->
      deferred.cancel()
    }
    pendingRequests.clear()

    sseEvents.close()
    _onClose()
  }

  suspend fun handlePost(call: ApplicationCall, messages: List<JSONRPCMessage>, respondAsBatch: Boolean) {
    if (!started.get()) {
      respondError(
        call,
        HttpStatusCode.InternalServerError,
        ErrorCode.Defined.InternalError,
        "Transport not started"
      )
      return
    }

    if (!call.accepts(ContentType.Application.Json)) {
      respondError(
        call,
        HttpStatusCode.NotAcceptable,
        ErrorCode.Defined.InvalidRequest,
        "Client must accept application/json",
      )
      return
    }

    val isInitializationRequest = messages.any { message ->
      message is JSONRPCRequest && message.method == Method.Defined.Initialize.value
    }

    var initializationReserved = false
    if (isInitializationRequest) {
      if (!initialized.compareAndSet(false, true)) {
        respondError(
          call,
          HttpStatusCode.BadRequest,
          ErrorCode.Defined.InvalidRequest,
          "Server already initialized for this session",
        )
        return
      }
      initializationReserved = true
    }
    else if (!initialized.get()) {
      respondError(
        call,
        HttpStatusCode.BadRequest,
        ErrorCode.Defined.InvalidRequest,
        "Session not initialized. Send initialize request first.",
      )
      return
    }

    val requestMessages = messages.filterIsInstance<JSONRPCRequest>()
    val responseDeferred = if (requestMessages.isNotEmpty()) {
      requestMessages.associate { request ->
        val deferred = CompletableDeferred<JSONRPCMessage>()
        pendingRequests[request.id] = deferred
        request.id to deferred
      }
    }
    else {
      emptyMap()
    }

    try {
      for (message in messages) {
        _onMessage(message)
      }
    }
    catch (t: Throwable) {
      if (t is CancellationException) throw t
      requestMessages.forEach { pendingRequests.remove(it.id)?.completeExceptionally(t) }
      if (initializationReserved) {
        initialized.set(false)
      }
      respondError(
        call,
        HttpStatusCode.BadRequest,
        ErrorCode.Defined.InternalError,
        "Failed to process request: ${t.message ?: t.javaClass.simpleName}",
      )
      return
    }

    if (requestMessages.isEmpty()) {
      // Notifications or responses only – acknowledge without payload
      attachSessionHeaders(call)
      call.respondText(
        text = "",
        contentType = ContentType.Application.Json,
        status = HttpStatusCode.Accepted,
      )
      return
    }

    val responses = mutableListOf<JSONRPCMessage>()
    try {
      for ((id, deferred) in responseDeferred) {
        val response = deferred.await()
        responses.add(response)
        pendingRequests.remove(id)
      }
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (t: Throwable) {
      responseDeferred.keys.forEach { pendingRequests.remove(it) }
      if (initializationReserved) {
        initialized.set(false)
      }
      respondError(
        call,
        HttpStatusCode.InternalServerError,
        ErrorCode.Defined.InternalError,
        "Failed to produce response: ${t.message ?: t.javaClass.simpleName}",
      )
      return
    }

    val payload = when {
      responses.isEmpty() -> "[]"
      responses.size == 1 && !respondAsBatch -> McpJson.encodeToString(responses.first())
      else -> McpJson.encodeToString(responses)
    }

    attachSessionHeaders(call)
    call.respondText(payload, ContentType.Application.Json)
  }

  suspend fun handleGet(call: ApplicationCall) {
    if (!initialized.get()) {
      respondError(
        call,
        HttpStatusCode.BadRequest,
        ErrorCode.Defined.InvalidRequest,
        "Session not initialized",
      )
      return
    }

    if (!call.accepts(ContentType.Text.EventStream)) {
      respondError(
        call,
        HttpStatusCode.NotAcceptable,
        ErrorCode.Defined.InvalidRequest,
        "Client must accept text/event-stream",
      )
      return
    }

    if (!hasActiveSse.compareAndSet(false, true)) {
      respondError(
        call,
        HttpStatusCode.Conflict,
        ErrorCode.Defined.InvalidRequest,
        "An SSE stream is already active for this session",
      )
      return
    }

    attachSessionHeaders(call)
    call.respondTextWriter(
      contentType = ContentType.Text.EventStream,
      status = HttpStatusCode.OK,
    ) {
      try {
        while (!closing.get()) {
          val event = sseEvents.receiveCatching().getOrNull() ?: break
          write(event)
          flush()
        }
      }
      finally {
        hasActiveSse.set(false)
      }
    }
  }

  suspend fun handleDelete(call: ApplicationCall) {
    if (!initialized.get()) {
      respondError(
        call,
        HttpStatusCode.BadRequest,
        ErrorCode.Defined.InternalError,
        "Session not initialized",
      )
      return
    }

    close()
    attachSessionHeaders(call)
    call.respondText("", ContentType.Application.Json, HttpStatusCode.OK)
  }

  private fun attachSessionHeaders(call: ApplicationCall) {
    call.response.headers.append(MCP_SESSION_ID_HEADER, sessionId, safeOnly = false)
    call.response.headers.append(MCP_PROTOCOL_VERSION_HEADER, negotiatedProtocolVersion, safeOnly = false)
    call.response.headers.append(HttpHeaders.CacheControl, "no-cache", safeOnly = false)
  }

  private fun emitToSse(message: JSONRPCMessage) {
    if (!hasActiveSse.get()) return

    val payload = buildString {
      append("event: ")
      append(SSE_EVENT_NAME)
      append('\n')
      append("data: ")
      append(McpJson.encodeToString(message))
      append("\n\n")
    }
    sseEvents.trySend(payload)
  }

  private fun extractProtocolVersion(response: JSONRPCResponse): String? {
    val result = response.result ?: return null
    return when (result) {
      is InitializeResult -> result.protocolVersion
      else -> result._meta["protocolVersion"]?.jsonPrimitive?.content
    }
  }

  private fun ApplicationCall.accepts(contentType: ContentType): Boolean {
    val rawHeader = request.headers[HttpHeaders.Accept] ?: return true
    val entries = rawHeader.split(',')
      .mapNotNull { entry ->
        val normalized = entry.trim()
        if (normalized.isEmpty()) null else normalized.lowercase()
      }

    if (entries.isEmpty()) return true

    val mime = "${contentType.contentType}/${contentType.contentSubtype}"
    val wildcard = "${contentType.contentType}/*"
    return entries.any { entry ->
      entry == "*/*" ||
      entry == mime ||
      entry.startsWith("$mime;") ||
      entry == wildcard ||
      entry.startsWith("$wildcard;")
    }
  }

  private fun String.take(maxLength: Int): String =
    if (length <= maxLength) this else substring(0, min(length, maxLength))

  private suspend fun respondError(
    call: ApplicationCall,
    status: HttpStatusCode,
    code: ErrorCode,
    message: String,
  ) {
    val jsonError = JSONRPCError(
      code = code,
      message = message.take(MAX_ERROR_MESSAGE_LENGTH)
    )

    call.respondText(McpJson.encodeToString(jsonError), ContentType.Application.Json, status)
  }

  companion object {
    private const val SSE_EVENT_NAME = "message"
    private const val MAX_ERROR_MESSAGE_LENGTH = 1024

    const val MCP_SESSION_ID_HEADER: String = "mcp-session-id"
    const val MCP_PROTOCOL_VERSION_HEADER: String = "mcp-protocol-version"
  }
}
