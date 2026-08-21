package com.intellij.mcpserver.impl.util.network

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.KtorDsl
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = logger<RoutingContext>()

/**
 * MCP Streamable HTTP session header.
 */
internal const val MCP_SESSION_ID_HEADER: String = "mcp-session-id"

internal val SSE_HEARTBEAT_PERIOD = 5.seconds

private val SSE_HEARTBEAT_EVENT = ServerSentEvent(comments = "heartbeat")

/**
 * Inactivity is the only signal an abandoned session gives: the Kotlin MCP client never sends DELETE, and a client may
 * work without a notification stream at all, so nothing else distinguishes it from an idle one.
 */
private val STREAMABLE_SESSION_IDLE_TIMEOUT = 5.minutes

/**
 * A Streamable HTTP session, which lives independently of the requests that use it: notification streams come and go,
 * and a client may never open one.
 */
private class StreamableSession(val transport: StreamableHttpServerTransport) {
  private data class Usage(val inFlight: Int = 0, val everStarted: Long = 0)

  private val usage = MutableStateFlow(Usage())

  suspend fun <T> whileInUse(operation: suspend () -> T): T {
    usage.update { Usage(inFlight = it.inFlight + 1, everStarted = it.everStarted + 1) }
    try {
      return operation()
    }
    finally {
      usage.update { it.copy(inFlight = it.inFlight - 1) }
    }
  }

  suspend fun awaitUnused(idleTimeout: Duration) {
    while (true) {
      val idleSince = awaitNothingInFlight()
      if (!usedWithin(idleTimeout, since = idleSince)) return
    }
  }

  private suspend fun awaitNothingInFlight(): Usage = usage.first { it.inFlight == 0 }

  private suspend fun usedWithin(timeout: Duration, since: Usage): Boolean =
    withTimeoutOrNull(timeout) { usage.first { it != since } } != null
}

@KtorDsl
fun Application.mcpPatched(
  prePhase: suspend PipelineContext<*, PipelineCall>.() -> Unit,
  block: suspend (ApplicationCall, Transport) -> Pair<ServerSession, CoroutineScope>,
) {
  val sseTransports = ConcurrentMap<String, SseServerTransport>()
  val streamableSessions = ConcurrentMap<String, StreamableSession>()
  val streamableSessionScopes = ConcurrentMap<String, CoroutineScope>()

  install(SSE)
  install(ContentNegotiation) { json(McpJson) }

  routing {
    intercept(ApplicationCallPipeline.Plugins) {
      prePhase()
      if (context.request.httpMethod == HttpMethod.Get) {
        val sessionId = context.request.header(MCP_SESSION_ID_HEADER)
        if (sessionId != null && streamableSessions[sessionId] != null) {
          context.response.header(MCP_SESSION_ID_HEADER, sessionId)
        }
      }
    }

    sse("/sse") {
      heartbeat {
        period = SSE_HEARTBEAT_PERIOD
      }

      mcpSseEndpoint("/message", sseTransports, block)
    }

    post("/message") {
      mcpPostEndpoint(sseTransports)
    }

    route("/stream") {
      sse {
        val sessionId = call.request.headers[MCP_SESSION_ID_HEADER]
        if (sessionId.isNullOrEmpty()) {
          call.respond(HttpStatusCode.BadRequest, "Missing $MCP_SESSION_ID_HEADER header")
          return@sse
        }

        val session = streamableSessions[sessionId]
        if (session == null) {
          call.respond(HttpStatusCode.NotFound, "Streamable HTTP session not found")
          return@sse
        }

        session.whileInUse { serveNotificationStream(session.transport) }
      }

      post {
        val session = obtainOrCreateStreamableSession(call,
                                                     streamableSessions,
                                                     this@mcpPatched,
                                                     block,
                                                     streamableSessionScopes) ?: return@post
        session.whileInUse { session.transport.handleRequest(null, call) }
      }

      delete {
        val session = existingStreamableSession(call, streamableSessions) ?: return@delete
        session.transport.handleRequest(null, call)
      }
    }
  }
}

private suspend fun ServerSSESession.mcpSseEndpoint(
  postEndpoint: String,
  transports: ConcurrentMap<String, SseServerTransport>,
  block: suspend (ApplicationCall, Transport) -> Pair<ServerSession, CoroutineScope>,
) {
  val transport = mcpSseTransport(postEndpoint, transports)

  val (serverSession, _) = block(call, ClientDisconnectTolerantTransport(transport))

  serverSession.onClose {
    logger.trace { "Server connection closed for sessionId: ${transport.sessionId}" }
    transports.remove(transport.sessionId)
  }

  logger.trace { "Server connected to transport for sessionId: ${transport.sessionId}" }
  awaitCancellation()
}

internal fun ServerSSESession.mcpSseTransport(
  postEndpoint: String,
  transports: ConcurrentMap<String, SseServerTransport>,
): SseServerTransport {
  val transport = SseServerTransport(postEndpoint, this)
  transport.onError {
    logger.error("Error in SSE connection", it)
  }
  transports[transport.sessionId] = transport

  logger.trace { "New SSE connection established and stored with sessionId: ${transport.sessionId}" }

  return transport
}

internal suspend fun RoutingContext.mcpPostEndpoint(
  transports: ConcurrentMap<String, SseServerTransport>,
) {
  val sessionId: String = call.request.queryParameters["sessionId"]
                          ?: run {
                            call.respond(HttpStatusCode.BadRequest, "sessionId query parameter is not provided")
                            return
                          }

  logger.trace { "Received message for sessionId: $sessionId" }

  val transport = transports[sessionId]
  if (transport == null) {
    logger.warn("Session not found for sessionId: $sessionId")
    call.respond(HttpStatusCode.NotFound, "Session not found")
    return
  }

  transport.handlePostMessage(call)
  logger.trace { "Message handled for sessionId: $sessionId" }
}

/**
 * Returns the session already associated with the `mcp-session-id` header, or responds with an error
 * and returns `null`. Used for GET and DELETE.
 */
private suspend fun existingStreamableSession(
  call: ApplicationCall,
  sessions: ConcurrentMap<String, StreamableSession>,
): StreamableSession? {
  val sessionId = call.request.headers[MCP_SESSION_ID_HEADER]
  if (sessionId.isNullOrEmpty()) {
    call.respond(HttpStatusCode.BadRequest, "Missing $MCP_SESSION_ID_HEADER header")
    return null
  }
  val session = sessions[sessionId]
  if (session == null) {
    call.respond(HttpStatusCode.NotFound, "Streamable HTTP session not found")
    return null
  }
  return session
}

private suspend fun obtainOrCreateStreamableSession(
  call: ApplicationCall,
  sessions: ConcurrentMap<String, StreamableSession>,
  scope: CoroutineScope,
  block: suspend (ApplicationCall, Transport) -> Pair<ServerSession, CoroutineScope>,
  streamableSessionScopes: ConcurrentMap<String, CoroutineScope>,
): StreamableSession? {
  val incomingSessionId = call.request.headers[MCP_SESSION_ID_HEADER]
  if (incomingSessionId != null) {
    val existing = sessions[incomingSessionId]
    if (existing != null) return existing
    call.respond(HttpStatusCode.NotFound, "Streamable HTTP session not found")
    return null
  }

  val transport = StreamableHttpServerTransport(
    StreamableHttpServerTransport.Configuration(enableJsonResponse = true)
  )
  val session = StreamableSession(transport)

  transport.setOnSessionInitialized { initializedId ->
    sessions[initializedId] = session
    scope.closeWhenAbandoned(initializedId, session)
    logger.trace { "New StreamableHttp session initialized with sessionId: $initializedId" }
  }
  transport.setOnSessionClosed { closedId ->
    sessions.remove(closedId)
    streamableSessionScopes.remove(closedId)
    logger.trace { "StreamableHttp session closed: $closedId" }
  }

  val (serverSession, scope) = block(call, ClientDisconnectTolerantTransport(transport))
  streamableSessionScopes[serverSession.sessionId] = scope
  transport.setSessionIdGenerator {
    serverSession.sessionId
  }
  serverSession.onClose {
    val id = transport.sessionId
    if (id != null) {
      sessions.remove(id)
      streamableSessionScopes.remove(id)
      logger.trace { "Server connection closed for StreamableHttp sessionId: $id" }
    }
  }

  return session
}

/**
 * Wraps a server transport so that a failure to deliver an outgoing response or notification to a client
 * that has already disconnected is treated as a routine disconnect instead of propagating to the MCP SDK's
 * `Protocol`, which logs every send failure as an error. Depending on which side of the teardown race the send
 * hits, an aborted HTTP request surfaces either as an [IOException] (`ClosedWriteChannelException` while writing
 * the response) or as an [IllegalStateException] ("No connection established for request ID ..." after the call's
 * job has already evicted the stream mapping). Send failures of server-initiated requests are still propagated
 * so that callers awaiting a response fail fast instead of waiting for a timeout.
 */
@ApiStatus.Internal
class ClientDisconnectTolerantTransport(private val delegate: Transport) : Transport {
  override suspend fun start(): Unit = delegate.start()
  override suspend fun close(): Unit = delegate.close()
  override fun onClose(block: () -> Unit): Unit = delegate.onClose(block)
  override fun onError(block: (Throwable) -> Unit): Unit = delegate.onError(block)
  override fun onMessage(block: suspend (JSONRPCMessage) -> Unit): Unit = delegate.onMessage(block)

  override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
    try {
      delegate.send(message, options)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      if (message is JSONRPCRequest || (e !is IOException && e !is IllegalStateException)) throw e
      logger.debug("Client disconnected before an outgoing ${message::class.simpleName} could be delivered", e)
    }
  }
}

/**
 * Because cancelling this coroutine runs the same `finally`, a session is closed on server shutdown as well. Closing
 * the transport is what ends a session: its `onClose` callbacks unregister it and cancel the scope that serves it.
 */
private fun CoroutineScope.closeWhenAbandoned(sessionId: String, session: StreamableSession) {
  launch(CoroutineName("streamable-session-$sessionId")) {
    try {
      session.awaitUnused(STREAMABLE_SESSION_IDLE_TIMEOUT)
      logger.warn("Closing abandoned StreamableHttp session: $sessionId")
    }
    finally {
      withContext(NonCancellable) { session.transport.close() }
    }
  }
}

/**
 * Heartbeats keep intermediaries from dropping the stream. Neither the stream nor its heartbeat ends the MCP session
 * they belong to: a session outlives every stream opened for it.
 */
private suspend fun ServerSSESession.serveNotificationStream(transport: StreamableHttpServerTransport) {
  coroutineScope {
    val stream = launch(CoroutineName("sse-notification-stream")) { transport.handleRequest(this@serveNotificationStream, call) }
    val heartbeat = launch(CoroutineName("sse-heartbeat")) { heartbeatUntilDisconnected() }
    stream.endsTogetherWith(heartbeat)
  }
}

private fun Job.endsTogetherWith(other: Job) {
  invokeOnCompletion { other.cancel() }
  other.invokeOnCompletion { cancel() }
}

private suspend fun ServerSSESession.heartbeatUntilDisconnected() {
  while (trySendHeartbeat()) {
    delay(SSE_HEARTBEAT_PERIOD)
  }
}

/** A failed write is how a connection lost without a clean shutdown is noticed. */
private suspend fun ServerSSESession.trySendHeartbeat(): Boolean =
  try {
    send(SSE_HEARTBEAT_EVENT)
    true
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    logger.trace { "Notification stream is gone: ${e.message}" }
    false
  }

//–– your custom context element
class HttpRequestElement(val request: ApplicationRequest) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<HttpRequestElement>

  override val key: CoroutineContext.Key<*> = Key
}

//–– install interceptor at the Call phase
fun Application.installHttpRequestPropagation() {
  intercept(ApplicationCallPipeline.Call) {
    withContext(HttpRequestElement(this.context.request)) {
      proceed()
    }
  }
}

val CoroutineContext.httpRequestOrNull: ApplicationRequest? get() = get(HttpRequestElement)?.request
