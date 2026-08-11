package com.intellij.mcpserver.impl.util.network

import com.intellij.mcpserver.toolwindow.McpDiagnosticService
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
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
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

private val logger = logger<RoutingContext>()

/**
 * MCP Streamable HTTP session header.
 */
internal const val MCP_SESSION_ID_HEADER: String = "mcp-session-id"

private val SSE_HEARTBEAT_PERIOD = 5.seconds

private val SSE_HEARTBEAT_EVENT = ServerSentEvent(comments = "heartbeat")
private val PENDING_TRANSPORT_TIMEOUT = 15.seconds

@KtorDsl
fun Application.mcpPatched(
  prePhase: suspend PipelineContext<*, PipelineCall>.() -> Unit,
  block: suspend (ApplicationCall, Transport) -> Pair<ServerSession, CoroutineScope>,
) {
  val sseTransports = ConcurrentMap<String, SseServerTransport>()
  val streamableTransports = ConcurrentMap<String, StreamableHttpServerTransport>()
  // transports created during POST initialize but not yet connected to a GET SSE stream.
  val pendingTransports = ConcurrentMap<String, StreamableHttpServerTransport>()
  val streamableSessionScopes = ConcurrentMap<String, CoroutineScope>()

  install(SSE)
  install(ContentNegotiation) { json(McpJson) }

  routing {
    intercept(ApplicationCallPipeline.Plugins) {
      prePhase()
      if (context.request.httpMethod == HttpMethod.Get) {
        val sessionId = context.request.header(MCP_SESSION_ID_HEADER)
        if (sessionId != null && (streamableTransports[sessionId] != null || pendingTransports[sessionId] != null)) {
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
        try {
          if (sessionId.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Missing $MCP_SESSION_ID_HEADER header")
            return@sse
          }

          val transport = pendingTransports.remove(sessionId)?.also { streamableTransports[sessionId] = it }
                          ?: streamableTransports[sessionId]
          if (transport == null) {
            call.respond(HttpStatusCode.NotFound, "Streamable HTTP session not found")
            return@sse
          }

          launchSseHeartbeat()
          transport.handleRequest(this, call)
        }
        finally {
          if (sessionId != null) {
            //todo temp fix for flaky tests
            streamableTransports.remove(sessionId)
            streamableSessionScopes.remove(sessionId)?.cancel()
            service<McpDiagnosticService>().sessionEnded(sessionId = sessionId)
          }
        }
      }

      post {
        val transport = obtainOrCreateStreamableTransport(call,
                                                          streamableTransports,
                                                          pendingTransports,
                                                          this@mcpPatched,
                                                          block,
                                                          streamableSessionScopes) ?: return@post
        transport.handleRequest(null, call)
      }

      delete {
        val transport = existingStreamableTransport(call, streamableTransports) ?: return@delete
        transport.handleRequest(null, call)
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

  val (serverSession, _) = block(call, transport)

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
 * Returns the transport already associated with the `mcp-session-id` header, or responds with an error
 * and returns `null`. Used for GET and DELETE.
 */
private suspend fun existingStreamableTransport(
  call: ApplicationCall,
  transports: ConcurrentMap<String, StreamableHttpServerTransport>,
): StreamableHttpServerTransport? {
  val sessionId = call.request.headers[MCP_SESSION_ID_HEADER]
  if (sessionId.isNullOrEmpty()) {
    call.respond(HttpStatusCode.BadRequest, "Missing $MCP_SESSION_ID_HEADER header")
    return null
  }
  val transport = transports[sessionId]
  if (transport == null) {
    call.respond(HttpStatusCode.NotFound, "Streamable HTTP session not found")
    return null
  }
  return transport
}

/**
 * For POST: returns an existing transport from [activeTransports] if the client supplied a known
 * session id, otherwise creates a new transport, wires lifecycle callbacks, and hands it to [block].
 * Newly initialized transports are placed into [pendingTransports]. They are promoted to
 * [activeTransports] when the client opens the GET SSE stream. If the client does not open the
 * SSE stream within [PENDING_TRANSPORT_TIMEOUT], the transport is evicted and closed.
 */
private suspend fun obtainOrCreateStreamableTransport(
  call: ApplicationCall,
  activeTransports: ConcurrentMap<String, StreamableHttpServerTransport>,
  pendingTransports: ConcurrentMap<String, StreamableHttpServerTransport>,
  scope: CoroutineScope,
  block: suspend (ApplicationCall, Transport) -> Pair<ServerSession, CoroutineScope>,
  streamableSessionScopes: ConcurrentMap<String, CoroutineScope>,
): StreamableHttpServerTransport? {
  val incomingSessionId = call.request.headers[MCP_SESSION_ID_HEADER]
  if (incomingSessionId != null) {
    val existing = activeTransports[incomingSessionId] ?: pendingTransports[incomingSessionId]
    if (existing != null) return existing
    call.respond(HttpStatusCode.NotFound, "Streamable HTTP session not found")
    return null
  }

  val transport = StreamableHttpServerTransport(
    StreamableHttpServerTransport.Configuration(enableJsonResponse = true)
  )

  transport.setOnSessionInitialized { initializedId ->
    pendingTransports[initializedId] = transport
    logger.trace { "New StreamableHttp session initialized with sessionId: $initializedId" }

    // drop if client never opens a GET SSE stream.
    scope.launch(CoroutineName("pending-transport-timeout-$initializedId")) {
      delay(PENDING_TRANSPORT_TIMEOUT)
      if (pendingTransports.remove(initializedId) != null) {
        streamableSessionScopes.remove(initializedId)
        logger.warn("Pending StreamableHttp transport timed out without SSE stream: $initializedId")
        try { transport.close() } catch (_: Exception) {}
      }
    }
  }
  transport.setOnSessionClosed { closedId ->
    pendingTransports.remove(closedId)
    activeTransports.remove(closedId)
    streamableSessionScopes.remove(closedId)
    logger.trace { "StreamableHttp session closed: $closedId" }
  }

  val (serverSession, scope) = block(call, transport)
  streamableSessionScopes[serverSession.sessionId] = scope
  transport.setSessionIdGenerator {
    serverSession.sessionId
  }
  serverSession.onClose {
    val id = transport.sessionId
    if (id != null) {
      pendingTransports.remove(id)
      activeTransports.remove(id)
      streamableSessionScopes.remove(id)
      logger.trace { "Server connection closed for StreamableHttp sessionId: $id" }
    }
  }

  return transport
}

private fun ServerSSESession.launchSseHeartbeat() {
  launch(CoroutineName("sse-heartbeat")) {
    while (isActive) {
      send(SSE_HEARTBEAT_EVENT)
      delay(SSE_HEARTBEAT_PERIOD)
    }
  }
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
