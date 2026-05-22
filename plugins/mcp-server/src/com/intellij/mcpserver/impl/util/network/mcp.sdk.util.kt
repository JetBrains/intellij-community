package com.intellij.mcpserver.impl.util.network

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.install
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
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.KtorDsl
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

private val logger = logger<RoutingContext>()

/**
 * MCP Streamable HTTP session header.
 */
internal const val MCP_SESSION_ID_HEADER: String = "mcp-session-id"

private val SSE_HEARTBEAT_PERIOD = 5.seconds

@KtorDsl
fun Application.mcpPatched(
  prePhase: suspend PipelineContext<*, PipelineCall>.() -> Unit,
  block: suspend (ApplicationCall, Transport) -> ServerSession,
) {
  val sseTransports = ConcurrentMap<String, SseServerTransport>()
  val streamableTransports = ConcurrentMap<String, StreamableHttpServerTransport>()

  install(SSE)

  routing {
    intercept(ApplicationCallPipeline.Plugins) {
      prePhase()
      if (context.request.httpMethod == HttpMethod.Get) {
        val sessionId = context.request.header(MCP_SESSION_ID_HEADER)
        if (sessionId != null && streamableTransports[sessionId] != null) {
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
        heartbeat {
          period = SSE_HEARTBEAT_PERIOD
        }
        val transport = existingStreamableTransport(call, streamableTransports) ?: return@sse
        transport.handleRequest(this, call)
      }

      post {
        val transport = obtainOrCreateStreamableTransport(call, streamableTransports, block) ?: return@post
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
  block: suspend (ApplicationCall, Transport) -> ServerSession,
) {
  val transport = mcpSseTransport(postEndpoint, transports)

  val serverSession = block(call, transport)

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
 * For POST: returns an existing transport if the client supplied a known session id; otherwise creates
 * a new transport, wires session lifecycle callbacks against [transports], and hands it to [block] to
 * create the `ServerSession`. The SDK transport adds itself to [transports] from inside the
 * `setOnSessionInitialized` callback once the `initialize` JSON-RPC request is processed.
 */
private suspend fun obtainOrCreateStreamableTransport(
  call: ApplicationCall,
  transports: ConcurrentMap<String, StreamableHttpServerTransport>,
  block: suspend (ApplicationCall, Transport) -> ServerSession,
): StreamableHttpServerTransport? {
  val incomingSessionId = call.request.headers[MCP_SESSION_ID_HEADER]
  if (incomingSessionId != null) {
    val existing = transports[incomingSessionId]
    if (existing != null) return existing
    call.respond(HttpStatusCode.NotFound, "Streamable HTTP session not found")
    return null
  }

  val transport = StreamableHttpServerTransport(
    StreamableHttpServerTransport.Configuration(enableJsonResponse = true)
  )
  transport.setOnSessionInitialized { initializedId ->
    transports[initializedId] = transport
    logger.trace { "New StreamableHttp session initialized with sessionId: $initializedId" }
  }
  transport.setOnSessionClosed { closedId ->
    transports.remove(closedId)
    logger.trace { "StreamableHttp session closed: $closedId" }
  }

  val serverSession = block(call, transport)
  serverSession.onClose {
    val id = transport.sessionId
    if (id != null) {
      transports.remove(id)
      logger.trace { "Server connection closed for StreamableHttp sessionId: $id" }
    }
  }

  return transport
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
val CoroutineContext.mcpSessionId: String? get() = httpRequestOrNull?.queryParameters?.get("sessionId")
