package com.intellij.mcpserver.impl.util.network

import com.intellij.openapi.diagnostic.fileLogger
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
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val logger = fileLogger()

/**
 * MCP Streamable HTTP session header.
 */
internal const val MCP_SESSION_ID_HEADER: String = "mcp-session-id"

internal const val SSE_HEARTBEAT_PERIOD_REGISTRY_KEY: String = "mcp.server.sse.heartbeat.period.ms"

private val SSE_HEARTBEAT_EVENT = ServerSentEvent(comments = "heartbeat")

private val sseHeartbeatPeriod: Duration
  get() = Registry.intValue(SSE_HEARTBEAT_PERIOD_REGISTRY_KEY, 5_000).coerceAtLeast(1).milliseconds

/**
 * Serves the MCP endpoints of an IDE. The legacy SSE stream is on `/sse`, with its POST endpoint on `/message`.
 * Streamable HTTP is on `/stream`, for GET, POST and DELETE.
 *
 * [createSession] connects the MCP SDK [ServerSession] of a new session to the [Transport] it is given, and returns it.
 * Closing that transport is what ends the session: the `onClose` callbacks unregister it here, and cancel the scope
 * that serves it. This [Application] is the scope the sessions run in, so cancelling it closes every live session.
 */
@KtorDsl
fun Application.mcpPatched(
  prePhase: suspend PipelineContext<*, PipelineCall>.() -> Unit,
  createSession: suspend (ApplicationCall, Transport) -> ServerSession,
) {
  val sseTransports = ConcurrentMap<String, SseServerTransport>()
  val streamableSessions = ConcurrentMap<String, StreamableHttpSession>()

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
        period = sseHeartbeatPeriod
      }

      mcpSseEndpoint("/message", sseTransports, createSession)
    }

    post("/message") {
      mcpPostEndpoint(sseTransports)
    }

    route("/stream") {
      sse {
        // ktor commits the response before this handler runs, so the only way to refuse a stream is to end it.
        val session = call.sessionForStream(streamableSessions) ?: return@sse
        session.runRequest { serveNotificationStream(session.transport) }
      }

      post {
        val session = obtainOrCreateStreamableSession(call, streamableSessions, this@mcpPatched, createSession) ?: return@post
        session.serveRequest(call)
      }

      delete {
        val session = call.sessionForRequest(streamableSessions) ?: return@delete
        session.serveRequest(call)
      }
    }
  }
}

/** The stream is the session here: an SSE client has no way to reach a session whose stream has ended. */
private suspend fun ServerSSESession.mcpSseEndpoint(
  postEndpoint: String,
  transports: ConcurrentMap<String, SseServerTransport>,
  createSession: suspend (ApplicationCall, Transport) -> ServerSession,
) {
  val transport = mcpSseTransport(postEndpoint, transports)
  try {
    createSession(call, ClientDisconnectTolerantTransport(transport))
    logger.trace { "Server connected to transport for sessionId: ${transport.sessionId}" }
    awaitCancellation()
  }
  finally {
    transport.closeUninterruptibly()
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

private suspend fun RoutingContext.mcpPostEndpoint(
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

private suspend fun ApplicationCall.sessionForRequest(
  sessions: ConcurrentMap<String, StreamableHttpSession>,
): StreamableHttpSession? {
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

private fun ApplicationCall.sessionForStream(
  sessions: ConcurrentMap<String, StreamableHttpSession>,
): StreamableHttpSession? {
  val sessionId = request.headers[MCP_SESSION_ID_HEADER]
  if (sessionId.isNullOrEmpty()) {
    logger.trace { "A notification stream arrived without the $MCP_SESSION_ID_HEADER header" }
    return null
  }
  val session = sessions[sessionId]
  if (session == null) logger.trace { "A notification stream named an unknown Streamable HTTP session: $sessionId" }
  return session
}

private suspend fun StreamableHttpSession.serveRequest(call: ApplicationCall) {
  if (!runRequest { transport.handleRequest(null, call) }) call.respondSessionNotFound()
}

private suspend fun ApplicationCall.respondSessionNotFound() {
  respond(HttpStatusCode.NotFound, "Streamable HTTP session not found")
}

private suspend fun obtainOrCreateStreamableSession(
  call: ApplicationCall,
  sessions: ConcurrentMap<String, StreamableHttpSession>,
  serverScope: CoroutineScope,
  createSession: suspend (ApplicationCall, Transport) -> ServerSession,
): StreamableHttpSession? {
  if (call.request.headers[MCP_SESSION_ID_HEADER] != null) return call.sessionForRequest(sessions)

  val transport = StreamableHttpServerTransport(
    StreamableHttpServerTransport.Configuration(enableJsonResponse = true)
  )
  val session = StreamableHttpSession(transport)

  val serverSession = try {
    createSession(call, ClientDisconnectTolerantTransport(transport))
  }
  catch (e: Throwable) {
    transport.closeUninterruptibly()
    throw e
  }

  val sessionId = serverSession.sessionId
  transport.setSessionIdGenerator { sessionId }
  // Registered before the entry exists, so no close can leave the entry behind.
  transport.onClose {
    sessions.remove(sessionId)
    logger.trace { "Streamable HTTP session unregistered: $sessionId" }
  }
  sessions[sessionId] = session
  serverScope.launch(CoroutineName("mcp-streamable-session-close/$sessionId")) {
    session.closeWhenAbandoned(sessionId)
  }
  logger.trace { "New Streamable HTTP session created with sessionId: $sessionId" }

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
    catch (e: Exception) {
      rethrowControlFlowException(e)
      if (message is JSONRPCRequest || (e !is IOException && e !is IllegalStateException)) throw e
      logger.debug("Client disconnected before an outgoing ${message::class.simpleName} could be delivered", e)
    }
  }
}

/** A close must survive the cancellation that asked for it, so it runs outside the cancelled scope. */
internal suspend fun Transport.closeUninterruptibly() {
  withContext(NonCancellable) { close() }
}

/** Heartbeats keep an intermediary from dropping the stream. */
private suspend fun ServerSSESession.serveNotificationStream(transport: StreamableHttpServerTransport) {
  coroutineScope {
    val stream = launch(CoroutineName("mcp-sse-notification-stream")) {
      transport.handleRequest(this@serveNotificationStream, call)
    }
    val heartbeat = launch(CoroutineName("mcp-sse-heartbeat")) {
      while (trySendHeartbeat()) {
        delay(sseHeartbeatPeriod)
      }
    }
    stream.invokeOnCompletion { heartbeat.cancel() }
    heartbeat.invokeOnCompletion { stream.cancel() }
  }
}

/**
 * A failed write is how a connection lost without a clean shutdown is noticed. It ends the stream, and so it also
 * bounds the hold the stream keeps on its session.
 */
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

class HttpRequestElement(val request: ApplicationRequest) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<HttpRequestElement>

  override val key: CoroutineContext.Key<*> = Key
}

fun Application.installHttpRequestPropagation() {
  intercept(ApplicationCallPipeline.Call) {
    withContext(HttpRequestElement(this.context.request)) {
      proceed()
    }
  }
}

val CoroutineContext.httpRequestOrNull: ApplicationRequest? get() = get(HttpRequestElement)?.request
