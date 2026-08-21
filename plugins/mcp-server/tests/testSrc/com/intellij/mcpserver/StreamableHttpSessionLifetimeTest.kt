// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.impl.util.network.SSE_HEARTBEAT_PERIOD
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import io.modelcontextprotocol.kotlin.sdk.types.DEFAULT_NEGOTIATED_PROTOCOL_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.time.Duration.Companion.seconds

private const val MCP_SESSION_ID_HEADER = "mcp-session-id"
private val ACCEPTED_CONTENT_TYPES = "${ContentType.Application.Json}, ${ContentType.Text.EventStream}"

private val INITIALIZE_REQUEST =
  """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"$DEFAULT_NEGOTIATED_PROTOCOL_VERSION",""" +
  """"capabilities":{},"clientInfo":{"name":"session lifetime test","version":"1.0"}}}"""
private const val INITIALIZED_NOTIFICATION = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
private const val TOOLS_LIST_REQUEST = """{"jsonrpc":"2.0","id":2,"method":"tools/list"}"""

/** A dead SSE connection is noticed only on the next write into it, so allow the server two heartbeats plus slack. */
private val DISCONNECT_PROPAGATION_DELAY = SSE_HEARTBEAT_PERIOD * 2 + 2.seconds

private const val NOTIFICATION_STREAM_RECONNECTS = 2

private const val REQUEST_ONLY_POLLS = 10
private val REQUEST_ONLY_POLL_PERIOD = 2.seconds

/**
 * A Streamable HTTP session is identified by its `mcp-session-id` and lives independently of any single HTTP request:
 * the standalone GET notification stream may be dropped and reopened at will, and a client that never opens one at all
 * must keep working. See IJPL-246574.
 */
@TestApplication
class StreamableHttpSessionLifetimeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val project by projectFixture
  }

  @Test
  @Timeout(120)
  fun session_outlives_notification_stream_reconnects(): Unit = streamableHttpSession {
    repeat(NOTIFICATION_STREAM_RECONNECTS) { reconnect ->
      openAndDropNotificationStream()
      delay(DISCONNECT_PROPAGATION_DELAY)

      listTools().assertOk("request after notification stream reconnect #$reconnect")
    }
  }

  @Test
  @Timeout(120)
  fun session_survives_active_use_without_a_notification_stream(): Unit = streamableHttpSession {
    repeat(REQUEST_ONLY_POLLS) { poll ->
      delay(REQUEST_ONLY_POLL_PERIOD)

      listTools().assertOk("request #${poll + 1} of a session that never opened a notification stream")
    }
  }

  private fun streamableHttpSession(action: suspend McpSessionUnderTest.() -> Unit) = runBlocking(Dispatchers.Default) {
    McpServerService.getInstance().start()
    try {
      val url = checkNotNull(McpServerConnectionAddressProvider.getInstanceOrNull()) { "No MCP address provider" }.serverStreamUrl
      HttpClient().use { client ->
        McpSessionUnderTest(client, url, project.basePath).apply { initialize() }.action()
      }
    }
    finally {
      McpServerService.getInstance().stop()
    }
  }
}

private class McpSessionUnderTest(
  private val client: HttpClient,
  private val url: String,
  private val projectBasePath: String?,
) {
  private var assignedSessionId: String? = null

  suspend fun initialize() {
    val initialize = postJsonRpc(INITIALIZE_REQUEST)
    initialize.assertOk("initialize")

    assignedSessionId = checkNotNull(initialize.headers[MCP_SESSION_ID_HEADER]) { "initialize did not assign a session id" }
    postJsonRpc(INITIALIZED_NOTIFICATION)
  }

  suspend fun listTools(): HttpResponse = postJsonRpc(TOOLS_LIST_REQUEST)

  /** A dedicated [HttpClient] is used because the server observes the disconnect only when the connection goes away. */
  suspend fun openAndDropNotificationStream() {
    HttpClient().use { streamClient ->
      streamClient.prepareGet(url) {
        header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
        mcpHeaders()
      }.execute { response ->
        response.assertStreaming("notification stream of a live session")
        assertThat(response.bodyAsChannel().readUTF8Line()).describedAs("first streamed line").isNotNull()
      }
    }
  }

  private suspend fun postJsonRpc(body: String): HttpResponse =
    client.post(url) {
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

/** Asserts on a response whose body ends, so the body can be quoted when the assertion fails. */
private suspend fun HttpResponse.assertOk(what: String) {
  assertThat(status).describedAs("$what: ${bodyAsText()}").isEqualTo(HttpStatusCode.OK)
}

/** Asserts on a response that keeps streaming, whose body must therefore never be read to its end. */
private fun HttpResponse.assertStreaming(what: String) {
  assertThat(status).describedAs(what).isEqualTo(HttpStatusCode.OK)
}
