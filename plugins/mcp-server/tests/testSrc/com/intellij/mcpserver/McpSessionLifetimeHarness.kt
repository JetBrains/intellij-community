// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.impl.util.network.MCP_SESSION_ID_HEADER
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.mcpserver.testFramework.waitUntilNotNull
import com.intellij.mcpserver.toolwindow.McpDiagnosticService
import com.intellij.openapi.components.service
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.waitUntil
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.DEFAULT_NEGOTIATED_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.InitializedNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.Notification
import io.modelcontextprotocol.kotlin.sdk.types.Request
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import java.net.Socket
import java.net.URI
import java.util.EnumSet
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

private val ACCEPTED_CONTENT_TYPES = "${ContentType.Application.Json}, ${ContentType.Text.EventStream}"

private val CLIENT_INFO = Implementation(name = "session lifetime test", version = "1.0")

/** The reason a client is given for a session that is gone, which is what its own recovery keys on. */
private const val SESSION_NOT_FOUND_REASON = "Streamable HTTP session not found"

private const val SSE_DATA_FIELD = "data: "
private const val SSE_COMMENT_FIELD = ":"
private const val LEGACY_SESSION_PARAMETER = "sessionId="

/** The reason phrase follows, so a stream the server accepted is recognized by the prefix of its status line. */
private const val STREAM_ACCEPTED_STATUS = "HTTP/1.1 200"

/** A stream the server is serving says so at once, so a read that keeps blocking has gone wrong. */
private val STREAM_READ_TIMEOUT = 10.seconds

/** Every wait here is for something the server already decided, so only a loaded machine makes it slow. */
private val SERVER_EVENT_TIMEOUT = 20.seconds

/** The wire form the server parses. The SDK types build it, so a malformed request cannot be written here. */
private fun Request.toJsonRpc(): String = McpJson.encodeToString(JSONRPCRequest.serializer(), toJSON())

private fun Notification.toJsonRpc(): String = McpJson.encodeToString(JSONRPCNotification.serializer(), toJSON())

private fun initializeRequest(capabilities: ClientCapabilities): String =
  InitializeRequest(
    InitializeRequestParams(
      protocolVersion = DEFAULT_NEGOTIATED_PROTOCOL_VERSION,
      capabilities = capabilities,
      clientInfo = CLIENT_INFO,
    ),
  ).toJsonRpc()

private val INITIALIZED_NOTIFICATION: String = InitializedNotification().toJsonRpc()

private val TOOLS_LIST_REQUEST: String = ListToolsRequest().toJsonRpc()

/**
 * Collects what the server logs, and lets the log keep it as well. A test sees neither side of a report from a
 * coroutine on its own. An error logged from another thread is not collected for the test. [Action.RETHROW] from here
 * is swallowed while the platform reports an unhandled exception. So the log is watched, and asserted on afterwards.
 */
internal class WatchedLog : LoggedErrorProcessor() {
  private val errors = CopyOnWriteArrayList<String>()
  private val warnings = CopyOnWriteArrayList<String>()

  override fun processError(category: String, message: String, details: Array<out String?>, t: Throwable?): Set<Action> {
    errors += "$category: $message\n${t?.stackTraceToString().orEmpty()}"
    return EnumSet.of(Action.LOG)
  }

  override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
    warnings += message
    return super.processWarn(category, message, t)
  }

  fun assertNoErrors(period: String) {
    assertThat(errors).describedAs("errors logged $period").isEmpty()
  }

  suspend fun awaitWarning(announcement: String, matches: (String) -> Boolean) {
    waitUntil("the server reported that $announcement", timeout = SERVER_EVENT_TIMEOUT) { warnings.any(matches) }
  }
}

/** Session ids the IDE reports as connected, which is how a session it still holds is seen from the outside. */
internal fun trackedSessionIds(): Set<String> =
  service<McpDiagnosticService>().getSessions().mapTo(mutableSetOf()) { it.sessionId }

private suspend fun awaitSessionTracked(sessionId: String, role: String) {
  waitUntil("the IDE reports $role as connected", timeout = SERVER_EVENT_TIMEOUT) { sessionId in trackedSessionIds() }
}

internal suspend fun awaitSessionClosed(sessionId: String, role: String) {
  waitUntil("the IDE closed $role", timeout = SERVER_EVENT_TIMEOUT) { sessionId !in trackedSessionIds() }
}

/** The legacy transport keeps its session id to itself, so the one session that appears is the one just opened. */
internal suspend fun awaitSessionOpened(alreadyTracked: Set<String>): String =
  waitUntilNotNull("the IDE reports the opened session as connected", timeout = SERVER_EVENT_TIMEOUT) {
    (trackedSessionIds() - alreadyTracked).singleOrNull()
  }

internal class McpTestClient(
  private val client: HttpClient,
  private val projectBasePath: String?,
) {
  private val addresses = checkNotNull(McpServerConnectionAddressProvider.getInstanceOrNull()) { "No MCP address provider" }

  private var assignedSessionId: String? = null

  val sessionId: String get() = checkNotNull(assignedSessionId) { "The client has no session" }

  /** Re-initializing recovers a lost session, so this is not a one-time call. It returns once the IDE reports it. */
  suspend fun initialize(capabilities: ClientCapabilities = ClientCapabilities()) {
    assignedSessionId = null

    val initialize = postJsonRpc(initializeRequest(capabilities))
    initialize.assertOk("initialize")
    assignedSessionId = checkNotNull(initialize.headers[MCP_SESSION_ID_HEADER]) { "initialize did not assign a session id" }

    postJsonRpc(INITIALIZED_NOTIFICATION).assertAccepted("the initialized notification")
    awaitSessionTracked(sessionId, "the initialized session")
  }

  suspend fun listTools(): HttpResponse = postJsonRpc(TOOLS_LIST_REQUEST)

  suspend fun deleteSession(): HttpResponse =
    client.delete(addresses.serverStreamUrl) {
      header(HttpHeaders.Accept, ACCEPTED_CONTENT_TYPES)
      mcpHeaders()
    }

  /** Waits for the stream to be served before killing it, so that the server has something to lose. */
  suspend fun openAndKillNotificationStream() {
    withSseStream(addresses.serverStreamUrl, "the notification stream of a live session") { stream ->
      stream.awaitLine("sent a heartbeat") { it.startsWith(SSE_COMMENT_FIELD) }
      stream.kill()
    }
  }

  suspend fun <T> withLegacySseSession(served: suspend (LegacySseStream) -> T): T =
    withSseStream(addresses.serverSseUrl, "a legacy SSE stream") { stream ->
      val endpoint = stream.awaitLine("named its message endpoint") {
        it.startsWith(SSE_DATA_FIELD) && LEGACY_SESSION_PARAMETER in it
      }
      served(LegacySseStream(stream, client, addresses.httpUrl(endpoint.removePrefix(SSE_DATA_FIELD)), projectBasePath))
    }

  /** Opens the stream for [served] only, so that an assertion that fails cannot leave the socket open. */
  private suspend fun <T> withSseStream(url: String, streamName: String, served: suspend (RawSseStream) -> T): T {
    val endpoint = URI(url)
    val opened = withContext(Dispatchers.IO) { RawSseStream.connect(endpoint, sseGetRequest(endpoint)) }
    return opened.use { stream ->
      assertThat(stream.statusLine).describedAs("the response to $streamName").startsWith(STREAM_ACCEPTED_STATUS)
      served(stream)
    }
  }

  private fun sseGetRequest(endpoint: URI): String = buildString {
    append("GET ${endpoint.rawPath} HTTP/1.1\r\n")
    append("Host: ${endpoint.authority}\r\n")
    append("${HttpHeaders.Accept}: ${ContentType.Text.EventStream}\r\n")
    projectBasePath?.let { append("$IJ_MCP_SERVER_PROJECT_PATH: $it\r\n") }
    assignedSessionId?.let { append("$MCP_SESSION_ID_HEADER: $it\r\n") }
    append("\r\n")
  }

  private suspend fun postJsonRpc(body: String): HttpResponse =
    client.post(addresses.serverStreamUrl) {
      header(HttpHeaders.Accept, ACCEPTED_CONTENT_TYPES)
      contentType(ContentType.Application.Json)
      mcpHeaders()
      setBody(body)
    }

  private fun HttpRequestBuilder.mcpHeaders() {
    projectBasePath?.let { header(IJ_MCP_SERVER_PROJECT_PATH, it) }
    assignedSessionId?.let { header(MCP_SESSION_ID_HEADER, it) }
  }
}

/**
 * An SSE stream that speaks HTTP for itself. No HTTP client library abandons a connection the way a client whose
 * process is gone does, and a stream that ends politely is not the case the server has to survive.
 */
internal class RawSseStream private constructor(private val socket: Socket) : AutoCloseable {
  private val lines = socket.getInputStream().bufferedReader().lineSequence().iterator()

  val statusLine: String = lines.next()

  /** Reads past the response headers, and past anything else, until the stream says what is waited for. */
  suspend fun awaitLine(signal: String, matches: (String) -> Boolean): String = withContext(Dispatchers.IO) {
    while (lines.hasNext()) {
      val line = lines.next()
      if (matches(line)) return@withContext line
    }
    error("The stream ended, and it never $signal")
  }

  /** Resets the connection instead of closing it, which is what a client that dies leaves behind. */
  fun kill() {
    socket.setSoLinger(true, 0)
    socket.close()
  }

  override fun close() {
    socket.close()
  }

  companion object {
    /** Nothing else holds the socket until the handshake is read, so a failure closes it here. */
    fun connect(endpoint: URI, request: String): RawSseStream {
      val socket = Socket(endpoint.host, endpoint.port)
      try {
        socket.soTimeout = STREAM_READ_TIMEOUT.inWholeMilliseconds.toInt()
        socket.getOutputStream().apply {
          write(request.toByteArray())
          flush()
        }
        return RawSseStream(socket)
      }
      catch (e: Throwable) {
        socket.close()
        throw e
      }
    }
  }
}

/** The client half of the legacy transport: a stream that carries the replies, and the endpoint it named for requests. */
internal class LegacySseStream(
  private val stream: RawSseStream,
  private val client: HttpClient,
  private val messageUrl: String,
  private val projectBasePath: String?,
) {
  suspend fun initialize() {
    post(initializeRequest(ClientCapabilities())).assertAccepted("initialize of a legacy SSE session")
    post(INITIALIZED_NOTIFICATION).assertAccepted("the initialized notification of a legacy SSE session")
  }

  suspend fun listTools(): HttpResponse = post(TOOLS_LIST_REQUEST)

  fun kill() = stream.kill()

  private suspend fun post(body: String): HttpResponse =
    client.post(messageUrl) {
      contentType(ContentType.Application.Json)
      projectBasePath?.let { header(IJ_MCP_SERVER_PROJECT_PATH, it) }
      setBody(body)
    }
}

/** Asserts on a response whose body ends, so the body can be quoted when the assertion fails. */
internal suspend fun HttpResponse.assertOk(request: String) {
  assertThat(status).describedAs("$request: ${bodyAsText()}").isEqualTo(HttpStatusCode.OK)
}

internal suspend fun HttpResponse.assertAccepted(request: String) {
  assertThat(status).describedAs("$request: ${bodyAsText()}").isEqualTo(HttpStatusCode.Accepted)
}

internal suspend fun HttpResponse.assertBadRequest(request: String) {
  assertThat(status).describedAs("$request: ${bodyAsText()}").isEqualTo(HttpStatusCode.BadRequest)
}

internal suspend fun HttpResponse.assertNotFound(request: String) {
  assertThat(status).describedAs("$request: ${bodyAsText()}").isEqualTo(HttpStatusCode.NotFound)
}

internal suspend fun HttpResponse.assertSessionNotFound(request: String) {
  val reason = bodyAsText()
  assertThat(status).describedAs("$request: $reason").isEqualTo(HttpStatusCode.NotFound)
  assertThat(reason).describedAs("the reason given for $request").isEqualTo(SESSION_NOT_FOUND_REASON)
}
