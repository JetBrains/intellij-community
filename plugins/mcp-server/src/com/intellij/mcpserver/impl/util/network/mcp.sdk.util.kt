package com.intellij.mcpserver.impl.util.network

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.install
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.ServerSSESession
import io.ktor.server.sse.sse
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.KtorDsl
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext


private val logger = logger<RoutingContext>()

/**
 * Temporary copied code from MCP SDK to pass thought sse session into handler
 */
@KtorDsl
fun Application.mcpPatched(prePhase: suspend PipelineContext<*, PipelineCall>.() -> Unit, block: suspend ServerSSESession.() -> Server) {
  val transports = ConcurrentMap<String, SseServerTransport>()

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
  }
}

private suspend fun ServerSSESession.mcpSseEndpoint(
  postEndpoint: String,
  transports: ConcurrentMap<String, SseServerTransport>,
  block: suspend ServerSSESession.() -> Server,
) {
  val transport =  mcpSseTransport(postEndpoint, transports)

  val server = this.block()

  server.onClose {
    logger.trace { "Server connection closed for sessionId: ${transport.sessionId}" }
    transports.remove(transport.sessionId)
  }

  server.connect(transport)
  logger.trace { "Server connected to transport for sessionId: ${transport.sessionId}" }
}

internal fun ServerSSESession.mcpSseTransport(
  postEndpoint: String,
  transports: ConcurrentMap<String, SseServerTransport>,
): SseServerTransport {
  val transport = SseServerTransport(postEndpoint, this)
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

//–– your custom context element
class HttpRequestElement(val request: ApplicationRequest) : CoroutineContext.Element {
  companion object Key : CoroutineContext.Key<HttpRequestElement>
  override val key: CoroutineContext.Key<*> = Key
}

//–– install interceptor at the Call phase
fun Application.installHttpRequestPropagation() {
  intercept(ApplicationCallPipeline.Call) {
    // wrap the rest of the pipeline in your element
    withContext(HttpRequestElement(this.context.request)) {
      proceed() // this continues routing, handlers, etc.
    }
  }
}

val CoroutineContext.httpRequestOrNull: ApplicationRequest? get() = get(HttpRequestElement)?.request
val CoroutineContext.mcpSessionId: String? get() = httpRequestOrNull?.queryParameters?.get("sessionId")
