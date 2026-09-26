package com.intellij.mcpserver.impl.util.network

import io.ktor.utils.io.ClosedWriteChannelException
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCNotification
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.RequestId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class ClientDisconnectTolerantTransportTest {
  private val response = JSONRPCResponse(id = RequestId.NumberId(6))
  private val notification = JSONRPCNotification(method = "notifications/tools/list_changed")
  private val request = JSONRPCRequest(id = 1, method = "elicitation/create")

  private class ThrowingTransport(private val sendFailure: Throwable? = null) : Transport {
    var lastSent: JSONRPCMessage? = null

    override suspend fun start() {}
    override suspend fun close() {}
    override fun onClose(block: () -> Unit) {}
    override fun onError(block: (Throwable) -> Unit) {}
    override fun onMessage(block: suspend (JSONRPCMessage) -> Unit) {}

    override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
      lastSent = message
      sendFailure?.let { throw it }
    }
  }

  @Test
  fun `successful send is delegated`() = runBlocking<Unit> {
    val delegate = ThrowingTransport()
    ClientDisconnectTolerantTransport(delegate).send(response)
    assertThat(delegate.lastSent).isEqualTo(response)
  }

  @Test
  fun `closed write channel while sending a response is swallowed`() = runBlocking {
    ClientDisconnectTolerantTransport(ThrowingTransport(ClosedWriteChannelException())).send(response)
  }

  @Test
  fun `io failure while sending a notification is swallowed`() = runBlocking {
    ClientDisconnectTolerantTransport(ThrowingTransport(IOException("Broken pipe"))).send(notification)
  }

  @Test
  fun `evicted stream mapping while sending a response is swallowed`() = runBlocking {
    val failure = IllegalStateException("No connection established for request ID: 6")
    ClientDisconnectTolerantTransport(ThrowingTransport(failure)).send(response)
  }

  @Test
  fun `send failure of a server-initiated request propagates`() {
    val transport = ClientDisconnectTolerantTransport(ThrowingTransport(ClosedWriteChannelException()))
    assertThrows<ClosedWriteChannelException> { runBlocking { transport.send(request) } }
  }

  @Test
  fun `cancellation propagates even though CancellationException extends IllegalStateException`() {
    val transport = ClientDisconnectTolerantTransport(ThrowingTransport(CancellationException("cancelled")))
    assertThrows<CancellationException> { runBlocking { transport.send(response) } }
  }

  @Test
  fun `unrelated failure while sending a response propagates`() {
    val transport = ClientDisconnectTolerantTransport(ThrowingTransport(RuntimeException("serialization failed")))
    assertThrows<RuntimeException> { runBlocking { transport.send(response) } }
  }
}
