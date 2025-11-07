package com.intellij.mcpserver.impl.util.network

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.InitializeResult
import io.modelcontextprotocol.kotlin.sdk.RequestId
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StreamableHttpServerTransportTest {

  @Test
  fun `send completes pending request and updates protocol version`() = runTest {
    val transport = StreamableHttpServerTransport()
    transport.start()

    val pendingRequests = transport.pendingRequests()
    val requestId = RequestId.NumberId(42L)
    val deferred = CompletableDeferred<JSONRPCMessage>()
    pendingRequests[requestId] = deferred

    val response = JSONRPCResponse(
      id = requestId,
      jsonrpc = "2.0",
      result = InitializeResult(
        protocolVersion = "1.1",
        capabilities = ServerCapabilities(),
        serverInfo = Implementation("test", "1.0"),
        _meta = buildJsonObject {}
      ),
      error = null,
    )

    val initialVersion = transport.negotiatedProtocolVersion()
    transport.send(response)

    assertTrue(deferred.isCompleted)
    assertEquals(response, deferred.await())
    assertEquals("1.1", transport.negotiatedProtocolVersion())
    assertNotEquals(initialVersion, transport.negotiatedProtocolVersion(), "Protocol version should update after initialize response")
  }

  @Test
  fun `send emits notification to SSE when subscriber present`() = runTest {
    val transport = StreamableHttpServerTransport()
    transport.start()

    val hasActiveSse = transport.hasActiveSse()
    hasActiveSse.set(true)
    val sseEvents = transport.sseEvents()

    val notification = JSONRPCNotification(
      method = "notifications/test",
      params = buildJsonObject { put("payload", JsonPrimitive("value")) },
      jsonrpc = "2.0",
    )

    transport.send(notification)

    val event = withTimeout(1_000) { sseEvents.receive() }
    val lines = event.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    assertTrue(lines.isNotEmpty(), "Expected at least one SSE line, got: '$event'")
    assertEquals("event: message", lines.first(), "Unexpected SSE event line: '$event'")
    val dataLine = lines.first { it.startsWith("data: ") }
    val payload = dataLine.removePrefix("data: ").trim()
    val json = McpJson.parseToJsonElement(payload)
    val expectedJson = buildJsonObject {
      put("method", JsonPrimitive("notifications/test"))
      put("params", buildJsonObject { put("payload", JsonPrimitive("value")) })
      put("jsonrpc", JsonPrimitive("2.0"))
    }
    assertEquals(expectedJson, json, "Unexpected payload in SSE data line")
  }

  @Test
  fun `send drops notification when no SSE subscriber`() = runTest {
    val transport = StreamableHttpServerTransport()
    transport.start()

    val sseEvents = transport.sseEvents()

    val notification = JSONRPCNotification(
      method = "notifications/test",
      params = JsonNull,
      jsonrpc = "2.0",
    )

    transport.send(notification)

    assertTrue(sseEvents.tryReceive().isFailure, "Notification should be dropped when there is no active SSE subscriber")
  }

  @Test
  fun `close cancels pending requests and closes SSE channel`() = runTest {
    val transport = StreamableHttpServerTransport()
    transport.start()

    val pendingRequests = transport.pendingRequests()
    val requestId = RequestId.NumberId(7L)
    val deferred = CompletableDeferred<JSONRPCMessage>()
    pendingRequests[requestId] = deferred
    val sseEvents = transport.sseEvents()

    transport.close()

    assertTrue(deferred.isCancelled)
    assertTrue(sseEvents.isClosedForSend)
    assertTrue(sseEvents.isClosedForReceive)
  }
}

@Suppress("UNCHECKED_CAST")
private fun StreamableHttpServerTransport.pendingRequests(): ConcurrentHashMap<RequestId, CompletableDeferred<JSONRPCMessage>> {
  return reflectField("pendingRequests")
}

private fun StreamableHttpServerTransport.sseEvents(): Channel<String> {
  return reflectField("sseEvents")
}

private fun StreamableHttpServerTransport.hasActiveSse(): AtomicBoolean {
  return reflectField("hasActiveSse")
}

private fun StreamableHttpServerTransport.negotiatedProtocolVersion(): String {
  return reflectField("negotiatedProtocolVersion")
}

@Suppress("UNCHECKED_CAST")
private fun <T> StreamableHttpServerTransport.reflectField(name: String): T {
  val field = StreamableHttpServerTransport::class.java.getDeclaredField(name)
  field.isAccessible = true
  return field.get(this) as T
}
