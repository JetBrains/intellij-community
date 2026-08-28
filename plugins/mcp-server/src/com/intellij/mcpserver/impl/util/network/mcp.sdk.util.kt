package com.intellij.mcpserver.impl.util.network

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.registry.Registry
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = logger<RoutingContext>()

/**
 * MCP Streamable HTTP session header.
 */
internal const val MCP_SESSION_ID_HEADER: String = "mcp-session-id"

internal val SSE_HEARTBEAT_PERIOD = 5.seconds

private val SSE_HEARTBEAT_EVENT = ServerSentEvent(comments = "heartbeat")

internal const val STREAMABLE_SESSION_IDLE_TIMEOUT_REGISTRY_KEY: String = "mcp.server.streamable.session.idle.timeout.ms"

private const val ABANDONED_SESSION_CLOSED = "Closing an abandoned Streamable HTTP session"

/** A test asserts on this text, so a reword changes an observable. */
internal const val UNINITIALIZED_SESSION_CLOSED: String = "$ABANDONED_SESSION_CLOSED that never initialized"

/**
 * Inactivity is the only signal an abandoned session gives: a client is not obliged to send DELETE and the SDK ones do
 * not do it on their own, and a client may work without a notification stream at all, so nothing else distinguishes an
 * abandoned session from an idle one.
 */
private val streamableSessionIdleTimeout: Duration
  get() = Registry.intValue(STREAMABLE_SESSION_IDLE_TIMEOUT_REGISTRY_KEY, 300_000).coerceAtLeast(1).milliseconds

/**
 * A session that never initialized holds a global IDE override installed for it (document conflict resolution), so it
 * is given only enough time to follow its own POST with an `initialize`.
 */
private val UNINITIALIZED_GRACE_PERIOD = 30.seconds

/**
 * A Streamable HTTP session, which lives independently of the requests that use it: notification streams come and go,
 * and a client may never open one.
 */
private class StreamableSession(val transport: StreamableHttpServerTransport) {
  private sealed interface State {
    /** [requestsStarted] is what tells an idle moment apart from a later one that looks just like it. */
    data class Serving(val inFlight: Int, val requestsStarted: Long) : State

    data object Closed : State
  }

  private val state = MutableStateFlow<State>(State.Serving(inFlight = 0, requestsStarted = 0))

  private val idleTimeout: Duration
    get() =
      if (transport.sessionId == null) streamableSessionIdleTimeout.coerceAtMost(UNINITIALIZED_GRACE_PERIOD)
      else streamableSessionIdleTimeout

  init {
    transport.onClose { state.value = State.Closed }
  }

  /** Runs [request] on a session that is still open, or returns `null` because it is not. */
  suspend fun <T> ifOpen(request: suspend () -> T): T? {
    if (!startRequest()) return null
    try {
      return request()
    }
    finally {
      finishRequest()
    }
  }

  /**
   * Suspends until the session has been idle long enough to be considered abandoned and claims it, or returns `false`
   * because it was closed for another reason.
   */
  suspend fun awaitAbandoned(): Boolean {
    while (true) {
      val idle = awaitNothingInFlight() ?: return false
      if (!usedWithin(idleTimeout, since = idle) && state.compareAndSet(idle, State.Closed)) return true
    }
  }

  private fun startRequest(): Boolean {
    while (true) {
      val serving = state.value as? State.Serving ?: return false
      val started = serving.copy(inFlight = serving.inFlight + 1, requestsStarted = serving.requestsStarted + 1)
      if (state.compareAndSet(serving, started)) return true
    }
  }

  private fun finishRequest() {
    while (true) {
      val serving = state.value as? State.Serving ?: return
      if (state.compareAndSet(serving, serving.copy(inFlight = serving.inFlight - 1))) return
    }
  }

  private suspend fun awaitNothingInFlight(): State.Serving? =
    state.first { it !is State.Serving || it.inFlight == 0 } as? State.Serving

  private suspend fun usedWithin(timeout: Duration, since: State): Boolean =
    withTimeoutOrNull(timeout) { state.first { it != since } } != null
}

@KtorDsl
fun Application.mcpPatched(
  prePhase: suspend PipelineContext<*, PipelineCall>.() -> Unit,
  block: suspend (ApplicationCall, Transport) -> ServerSession,
) {
  val sseTransports = ConcurrentMap<String, SseServerTransport>()
  val streamableSessions = ConcurrentMap<String, StreamableSession>()

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
        val session = call.streamableSessionOrNull(streamableSessions) ?: return@sse
        session.ifOpen { serveNotificationStream(session.transport) }
      }

      post {
        val session = obtainOrCreateStreamableSession(call, streamableSessions, this@mcpPatched, block) ?: return@post
        session.ifOpen { session.transport.handleRequest(null, call) } ?: call.respondSessionNotFound()
      }

      delete {
        val session = call.streamableSession(streamableSessions) ?: return@delete
        session.ifOpen { session.transport.handleRequest(null, call) } ?: call.respondSessionNotFound()
      }
    }
  }
}

/** The stream is the session here: an SSE client has no way to reach a session whose stream has ended. */
private suspend fun ServerSSESession.mcpSseEndpoint(
  postEndpoint: String,
  transports: ConcurrentMap<String, SseServerTransport>,
  block: suspend (ApplicationCall, Transport) -> ServerSession,
) {
  val transport = mcpSseTransport(postEndpoint, transports)
  try {
    block(call, ClientDisconnectTolerantTransport(transport))
    logger.trace { "Server connected to transport for sessionId: ${transport.sessionId}" }
    awaitCancellation()
  }
  finally {
    withContext(NonCancellable) { transport.close() }
  }
}

private fun ServerSSESession.mcpSseTransport(
  postEndpoint: String,
  transports: ConcurrentMap<String, SseServerTransport>,
): SseServerTransport {
  val transport = SseServerTransport(postEndpoint, this)
  transport.onError {
    if (it is IOException) logger.debug("The SSE connection was lost", it)
    else logger.error("Error in SSE connection", it)
  }
  transport.onClose {
    transports.remove(transport.sessionId)
    logger.trace { "SSE session unregistered: ${transport.sessionId}" }
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
 * Returns the session named by the `mcp-session-id` header, or responds with the reason it cannot be served
 * and returns `null`.
 */
private suspend fun ApplicationCall.streamableSession(
  sessions: ConcurrentMap<String, StreamableSession>,
): StreamableSession? {
  val sessionId = request.headers[MCP_SESSION_ID_HEADER]
  if (sessionId.isNullOrEmpty()) {
    respond(HttpStatusCode.BadRequest, "Missing $MCP_SESSION_ID_HEADER header")
    return null
  }
  val session = sessions[sessionId]
  if (session == null) {
    respondSessionNotFound()
    return null
  }
  return session
}

/**
 * The response of a notification stream is already committed by the time its handler runs, so the only way to refuse
 * one is to end it.
 */
private fun ApplicationCall.streamableSessionOrNull(
  sessions: ConcurrentMap<String, StreamableSession>,
): StreamableSession? {
  val sessionId = request.headers[MCP_SESSION_ID_HEADER]
  val session = sessionId?.let(sessions::get)
  if (session == null) logger.trace { "No StreamableHttp session to serve a notification stream for: $sessionId" }
  return session
}

private suspend fun ApplicationCall.respondSessionNotFound() {
  respond(HttpStatusCode.NotFound, "Streamable HTTP session not found")
}

private suspend fun obtainOrCreateStreamableSession(
  call: ApplicationCall,
  sessions: ConcurrentMap<String, StreamableSession>,
  scope: CoroutineScope,
  block: suspend (ApplicationCall, Transport) -> ServerSession,
): StreamableSession? {
  if (call.request.headers[MCP_SESSION_ID_HEADER] != null) return call.streamableSession(sessions)

  val transport = StreamableHttpServerTransport(
    StreamableHttpServerTransport.Configuration(enableJsonResponse = true)
  )
  val session = StreamableSession(transport)

  val serverSession = try {
    block(call, ClientDisconnectTolerantTransport(transport))
  }
  catch (e: Throwable) {
    withContext(NonCancellable) { transport.close() }
    throw e
  }

  val sessionId = serverSession.sessionId
  transport.setSessionIdGenerator { sessionId }
  transport.onClose {
    sessions.remove(sessionId)
    logger.trace { "StreamableHttp session unregistered: $sessionId" }
  }
  sessions[sessionId] = session
  scope.closeWhenAbandoned(sessionId, session)
  logger.trace { "New StreamableHttp session created with sessionId: $sessionId" }

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
      if (session.awaitAbandoned()) {
        if (session.transport.sessionId != null) logger.info("$ABANDONED_SESSION_CLOSED. Id: $sessionId")
        else logger.warn("$UNINITIALIZED_SESSION_CLOSED. Id: $sessionId")
      }
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
