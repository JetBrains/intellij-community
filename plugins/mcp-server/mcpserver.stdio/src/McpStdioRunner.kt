package com.intellij.mcpserver.stdio

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*

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

  val sseClientTransport = SseClientTransport(httpClient, "http://localhost:$port/")

  val projectPath = System.getenv(IJ_MCP_SERVER_PROJECT_PATH)

  stdioServerTransport.onMessage {
    val updatedRequest = when {
      it is JSONRPCRequest && projectPath != null -> {
        JSONRPCRequest(it.id, it.method,decorateParamsIfNeeded (it.params, projectPath))
      }
      else -> {
        it
      }
    }
    sseClientTransport.send(updatedRequest)
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

private const val metaKey = "_meta"

private fun decorateParamsIfNeeded(params: JsonElement, projectPath: String?): JsonElement {
  if (projectPath == null) return params

  if (params is JsonObject) {
    val meta = params[metaKey]
    if (meta is JsonObject?) {
      return buildJsonObject { // params value
        for ((key, value) in params.entries) {
          if (key != metaKey) {
            put(key, value)
          }
          else {
            put(metaKey, buildJsonObject { // _meta value
              meta?.jsonObject?.let { metaJson ->
                for ((key, value) in metaJson) { // copy _meta members
                  put(key, value)
                }
              }
              put(IJ_MCP_SERVER_PROJECT_PATH, projectPath)
            })
          }
        }
      }
    }
  }
  return params
}

object ClassPathMarker