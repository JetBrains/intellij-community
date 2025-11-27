package com.intellij.mcpserver.impl.util.network

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.KtorDsl
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.Method
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.coroutines.CoroutineContext

private val logger = logger<RoutingContext>()

@KtorDsl
fun Application.mcpPatched(
  prePhase: suspend PipelineContext<*, PipelineCall>.() -> Unit,
  block: suspend (ApplicationCall, Transport) -> ServerSession,
) {
  val transports = ConcurrentMap<String, SseServerTransport>()
  val streamableSessions = ConcurrentMap<String, StreamableSession>()

  install(SSE)

  routing {
    intercept(ApplicationCallPipeline.Plugins) {
      prePhase()
    }

    sse("/sse") {
      mcpSseEndpoint("/message", transports, block)
    }

    post("/message") {
      mcpPostEndpoint(transports)
    }

    route("/stream") {
      post {
        handleStreamablePost(streamableSessions, block)
      }
      get {
        handleStreamableGet(streamableSessions)
      }
      delete {
        handleStreamableDelete(streamableSessions)
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

private suspend fun RoutingContext.handleStreamablePost(
  sessions: ConcurrentMap<String, StreamableSession>,
  block: suspend (ApplicationCall, Transport) -> ServerSession,
) {
  val rawBody = try {
    call.receiveText()
  }
  catch (t: Throwable) {
    if (t is CancellationException) throw t
    respondJsonError(HttpStatusCode.BadRequest, -32700, "Failed to read request body: ${t.message ?: "unknown error"}")
    return
  }

  val parsed = try {
    parseMessages(rawBody)
  }
  catch (t: Throwable) {
    if (t is CancellationException) throw t
    respondJsonError(
      HttpStatusCode.BadRequest,
      -32700,
      "Invalid JSON-RPC payload: ${t.message ?: t.javaClass.simpleName}",
    )
    return
  }

  val sessionHeader = call.request.headers[StreamableHttpServerTransport.MCP_SESSION_ID_HEADER]
  val hasInitialization = parsed.messages.any { message ->
    message is JSONRPCRequest && message.method == Method.Defined.Initialize.value
  }

  val session = when {
    sessionHeader != null -> sessions[sessionHeader]
                             ?: run {
                               respondJsonError(HttpStatusCode.NotFound, -32000, "Streamable HTTP session not found")
                               return
                             }
    hasInitialization -> createStreamableSession(call, sessions, block)
    else -> {
      respondJsonError(
        HttpStatusCode.BadRequest,
        -32000,
        "Missing ${StreamableHttpServerTransport.MCP_SESSION_ID_HEADER} header",
      )
      return
    }
  }

  session.transport.handlePost(call, parsed.messages, parsed.isBatch)
}

private suspend fun RoutingContext.handleStreamableGet(
  sessions: ConcurrentMap<String, StreamableSession>,
) {
  val session = findStreamableSession(sessions) ?: return
  session.transport.handleGet(call)
}

private suspend fun RoutingContext.handleStreamableDelete(
  sessions: ConcurrentMap<String, StreamableSession>,
) {
  val session = findStreamableSession(sessions) ?: return
  session.transport.handleDelete(call)
}

private suspend fun RoutingContext.findStreamableSession(
  sessions: ConcurrentMap<String, StreamableSession>,
): StreamableSession? {
  val sessionId = call.request.headers[StreamableHttpServerTransport.MCP_SESSION_ID_HEADER]
                  ?: run {
                    respondJsonError(
                      HttpStatusCode.BadRequest,
                      -32000,
                      "Missing ${StreamableHttpServerTransport.MCP_SESSION_ID_HEADER} header",
                    )
                    return null
                  }

  val session = sessions[sessionId]
  if (session == null) {
    respondJsonError(HttpStatusCode.NotFound, -32000, "Streamable HTTP session not found")
    return null
  }

  return session
}

private suspend fun createStreamableSession(
  applicationCall: ApplicationCall,
  sessions: ConcurrentMap<String, StreamableSession>,
  block: suspend (ApplicationCall, Transport) -> ServerSession,
): StreamableSession {
  val transport = StreamableHttpServerTransport()
  val serverSession = block(applicationCall, transport)

  transport.onError {
    logger.error("Error in Streamable HTTP transport", it)
  }
  transport.onClose {
    logger.trace { "Streamable HTTP transport closed for session ${transport.sessionId}" }
    sessions.remove(transport.sessionId)
  }
  serverSession.onClose {
    logger.trace { "Server closed for Streamable HTTP session ${transport.sessionId}" }
    sessions.remove(transport.sessionId)
  }

  serverSession.connect(transport)

  val session = StreamableSession(transport, serverSession)
  sessions[transport.sessionId] = session
  logger.trace { "Streamable HTTP session started with id ${transport.sessionId}" }
  return session
}

private fun parseMessages(body: String): ParsedMessages {
  val element: JsonElement = McpJson.parseToJsonElement(body)
  return when (element) {
    is JsonArray -> ParsedMessages(
      messages = element.map { McpJson.decodeFromJsonElement<JSONRPCMessage>(it) },
      isBatch = true,
    )
    else -> ParsedMessages(
      messages = listOf(McpJson.decodeFromJsonElement<JSONRPCMessage>(element)),
      isBatch = false,
    )
  }
}

private suspend fun RoutingContext.respondJsonError(status: HttpStatusCode, code: Int, message: String) {
  val json = buildJsonObject {
    put("jsonrpc", JsonPrimitive("2.0"))
    put(
      "error",
      buildJsonObject {
        put("code", JsonPrimitive(code))
        put("message", JsonPrimitive(message))
      },
    )
    put("id", JsonNull)
  }
  call.respondText(json.toString(), ContentType.Application.Json, status)
}

private data class ParsedMessages(val messages: List<JSONRPCMessage>, val isBatch: Boolean)
private data class StreamableSession(val transport: StreamableHttpServerTransport, val server: ServerSession)

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
