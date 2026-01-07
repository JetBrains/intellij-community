package com.intellij.mcpserver.stdio

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

private const val IJ_MCP_PREFIX = "IJ_MCP_"
const val IJ_MCP_HEADER_PREFIX: String = "${IJ_MCP_PREFIX}HEADER_"
val IJ_MCP_SERVER_PORT: String = ::IJ_MCP_SERVER_PORT.name
val IJ_MCP_SERVER_PROJECT_PATH: String = ::IJ_MCP_SERVER_PROJECT_PATH.name
private val IJ_MCP_DEBUG: String = ::IJ_MCP_DEBUG.name

suspend fun main() {
  val inputStream = System.`in`
  val outputStream = System.out

  val port = System.getenv(IJ_MCP_SERVER_PORT)?.toIntOrNull() ?: run {
    System.err.println("Please specify the port of the underlying MCP IDE server using the environment variable $IJ_MCP_SERVER_PORT")
    return
  }

  if (isDebugEnabled) {
    info("Debug mode enabled")
  }
  else {
    info("Debug mode can be enabled by setting the $IJ_MCP_DEBUG environment variable to any value (empty string or TRUE). Debug messages will be printed to stderr.")
  }

  val stdioServerTransport = StdioServerTransport(inputStream.asSource().buffered(), outputStream.asSink().buffered())

  val httpClient = HttpClient {
    install(SSE)
  }

  val envsToPass = System.getenv().filter { (key, _) -> key != IJ_MCP_DEBUG && key.startsWith(IJ_MCP_PREFIX) }
  debug("Passing the following headers to the server: ${envsToPass.entries.joinToString { (key, value) -> "$key=$value"}}")

  val sseClientTransport = SseClientTransport(httpClient, "http://localhost:$port/sse") {
    envsToPass.forEach { (key, value) -> header(key.removePrefix(IJ_MCP_HEADER_PREFIX), value)}
  }

  stdioServerTransport.onMessage {
    debug("STDIO->SSE: $it")
    sseClientTransport.send(it)
  }

  sseClientTransport.onMessage {
    debug("SSE->STDIO: $it")
    stdioServerTransport.send(it)
  }

  stdioServerTransport.onClose {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      info("STDIO closed -> closing SSE")
      sseClientTransport.close()
    }
  }

  sseClientTransport.onClose {
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      info("SSE closed -> closing STDIO")
      stdioServerTransport.close()
    }
  }

  stdioServerTransport.onError {
    error("Error in STDIO: $it")
  }
  sseClientTransport.onError {
    error("Error in SSE: $it")
  }
  sseClientTransport.start()
  stdioServerTransport.start()

  info("Proxy transports started")
  val finished = CompletableDeferred<Unit>()

  stdioServerTransport.onClose { finished.complete(Unit) }

  info("Waiting for the transports to finish")
  finished.await()
  info("Transports finished")
}

object ClassPathMarker

private val isDebugEnabled: Boolean = System.getenv(IJ_MCP_DEBUG)?.toBoolean() == true

private fun error(message: String) {
  System.err.println("[ERROR] $message")
}

private fun info(message: String) {
  System.err.println("[INFO] $message")
}

private fun debug(message: String) {
  if (isDebugEnabled) {
    System.err.println("[DEBUG] $message")
  }
}