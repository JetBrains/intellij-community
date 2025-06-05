package com.intellij.mcpserver.stdio

import com.intellij.mcpserver.stdio.mcpProto.*
import com.intellij.mcpserver.stdio.mcpProto.server.Server
import com.intellij.mcpserver.stdio.mcpProto.server.ServerOptions
import com.intellij.mcpserver.stdio.mcpProto.server.StdioServerTransport
import com.intellij.mcpserver.stdio.mcpProto.shared.McpJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.streams.asInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.io.asSink
import kotlinx.io.buffered

val IJ_MCP_SERVER_PORT: String = ::IJ_MCP_SERVER_PORT.name
val IJ_MCP_SERVER_NAME: String = ::IJ_MCP_SERVER_NAME.name
val IJ_MCP_SERVER_VERSION: String = ::IJ_MCP_SERVER_VERSION.name
val IJ_MCP_SERVER_PROJECT_PATH: String = ::IJ_MCP_SERVER_PROJECT_PATH.name

suspend fun main() {
  val inputStream = System.`in`
  val outputStream = System.out

  val httpClient = HttpClient()

  val port = System.getenv(IJ_MCP_SERVER_PORT)?.toIntOrNull() ?: run {
    println("Please specify the port of the underlying MCP IDE server using the environment variable $IJ_MCP_SERVER_PORT")
    return
  }

  val endpoint = "http://localhost:$port/api/mcp/"

  val response = httpClient.get(urlString = endpoint + "list_tools")

  val server = Server(
    serverInfo = Implementation(
      name = System.getenv(IJ_MCP_SERVER_NAME) ?: "IJ MCP Server",
      version = System.getenv(IJ_MCP_SERVER_VERSION) ?: "1.0.0"),
    options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(true))))

  val stdioServerTransport = StdioServerTransport(inputStream.asInput(), outputStream.asSink().buffered())

  server.setRequestHandler<ListToolsRequest>(Method.Defined.ToolsList) { request, extra ->
    val response = httpClient.get(urlString = endpoint + "list_tools") {
      headers {
        val projectPath = System.getenv(IJ_MCP_SERVER_PROJECT_PATH)
        if (!projectPath.isNullOrBlank()) {
          this.append(IJ_MCP_SERVER_PROJECT_PATH, projectPath)
        }
      }
    }
    when (response.status) {
      HttpStatusCode.OK -> {
        val responseText = response.bodyAsText()
        val tools = McpJson.decodeFromString<List<Tool>>(responseText)
        return@setRequestHandler ListToolsResult(tools = tools, nextCursor = null)
      }
      else -> {
        server.close()
        throw IllegalStateException("Failed listing tools from the underlying IDE server: ${response.status}")
      }
    }
  }

  server.setRequestHandler<CallToolRequest>(Method.Defined.ToolsCall) { request, extra ->
    val response = httpClient.post(urlString = endpoint + request.name) {
      headers {
        contentType(ContentType.Application.Json)
        val projectPath = System.getenv(IJ_MCP_SERVER_PROJECT_PATH)
        if (!projectPath.isNullOrBlank()) {
          this.append(IJ_MCP_SERVER_PROJECT_PATH, projectPath)
        }
      }
      setBody(McpJson.encodeToString(request.arguments))
    }
    when (response.status) {
      HttpStatusCode.Companion.OK -> {
        val responseText = response.bodyAsText()
        val response = McpJson.decodeFromString<Response>(responseText)
        return@setRequestHandler CallToolResult(content = listOf(TextContent(response.status ?: response.error
                                                                             ?: "<No message provided>")), isError = response.error != null)
      }
      else -> {
        server.close()
        throw IllegalStateException("Failed calling tool ${request.name} on the underlying IDE server: ${response.status}")
      }
    }
  }

  val finished = CompletableDeferred<Unit>()

  server.onClose {
    finished.complete(Unit)
  }
  server.connect(stdioServerTransport)
  finished.await()
}

object ClassPathMarker