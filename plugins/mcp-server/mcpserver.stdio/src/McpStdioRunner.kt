package com.intellij.mcpserver.stdio

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered

private const val IJ_MCP_PREFIX = "IJ_MCP_"
val IJ_MCP_SERVER_PORT: String = ::IJ_MCP_SERVER_PORT.name
val IJ_MCP_SERVER_PROJECT_PATH: String = ::IJ_MCP_SERVER_PROJECT_PATH.name


suspend fun main() {
  val inputStream = System.`in`
  val outputStream = System.out

  val port = System.getenv(IJ_MCP_SERVER_PORT)?.toIntOrNull() ?: run {
    System.err.println("Please specify the port of the underlying MCP IDE server using the environment variable $IJ_MCP_SERVER_PORT")
    return
  }

  val stdioServerTransport = StdioServerTransport(inputStream.asInput(), outputStream.asSink().buffered())

  val httpClient = HttpClient() {
    install(SSE)
  }

  val envsToPass = System.getenv().filter { (key, _) -> key.startsWith(IJ_MCP_PREFIX) }
  val sseClientTransport = SseClientTransport(httpClient, "http://localhost:$port/sse") {
    envsToPass.forEach { (key, value) -> header(key, value)}
  }

  stdioServerTransport.onMessage {
    sseClientTransport.send(it)
  }

  sseClientTransport.onMessage {
    stdioServerTransport.send(it)
  }

  stdioServerTransport.onClose {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      sseClientTransport.close()
    }
  }

  stdioServerTransport.onError {
    System.err.println("Error in StdIO: $it")
  }
  sseClientTransport.onError {
    System.err.println("Error in Socket: $it")
  }
  sseClientTransport.start()
  stdioServerTransport.start()

  val finished = CompletableDeferred<Unit>()

  stdioServerTransport.onClose { finished.complete(Unit) }

  finished.await()
}

object ClassPathMarker