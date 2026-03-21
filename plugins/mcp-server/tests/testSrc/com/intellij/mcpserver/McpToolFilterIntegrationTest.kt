@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.stdio.IJ_MCP_ALLOWED_TOOLS
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable

@Testable
@TestApplication
class McpToolFilterIntegrationTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project by projectFixture
  @Suppress("unused")
  private val moduleFixture = projectFixture.moduleFixture("testModule")

  private val tool1 = "list_directory_tree"
  private val tool2 = "open_file_in_editor"
  private val tool3 = "get_file_text_by_path"

  @Test
  fun `server exposes all tools with AllowAll filter`() = runBlocking(Dispatchers.Default) {
    withConnection(filter = McpToolFilter.AllowAll) { client, _ ->
      val tools = client.listTools().tools

      // Should have all available tools
      assertThat(tools).hasSizeGreaterThanOrEqualTo(10)

      // Verify some common tools are present
      assertThat(tools).anyMatch { it.name == tool1 }
      assertThat(tools).anyMatch { it.name == tool2 }
    }
  }


  @Test
  fun `server filters tools with AllowList filter`() = runBlocking(Dispatchers.Default) {
    val allowedTools = setOf(tool1, tool2)
    val filter = McpToolFilter.AllowList(allowedTools)

    withConnection(filter = filter) { client, _ ->
      val tools = client.listTools().tools

      // Should only have allowed tools
      assertThat(tools).hasSize(2)

      // Verify only allowed tools are present
      val toolNames = tools.map { it.name }.toSet()
      assertThat(toolNames).isEqualTo(allowedTools)

      // Verify filtered tools are not present
      assertThat(tools).noneMatch { it.name == tool3 }
    }
  }

  @Test
  fun `server filters tools with header-based filter`() = runBlocking(Dispatchers.Default) {
    val allowedToolsHeader = "$tool1,$tool2,$tool3"

    withConnectionUsingHeader(allowedToolsHeader = allowedToolsHeader) { client ->
      val tools = client.listTools().tools

      // Should only have tools from header
      assertThat(tools).hasSize(3)

      val toolNames = tools.map { it.name }.toSet()
      assertThat(toolNames).isEqualTo(setOf(tool1, tool2, tool3))
    }
  }

  @Test
  fun `server handles malformed header gracefully`() = runBlocking(Dispatchers.Default) {
    // Test with spaces and empty entries
    val allowedToolsHeader = " $tool1 , , $tool2 , "

    withConnectionUsingHeader(allowedToolsHeader = allowedToolsHeader) { client ->
      val tools = client.listTools().tools

      // Should filter out empty entries and trim spaces
      assertThat(tools).hasSize(2)

      val toolNames = tools.map { it.name }.toSet()
      assertThat(toolNames).isEqualTo(setOf(tool1, tool2))
    }
  }

  @Test
  fun `server exposes no tools with empty AllowList`() = runBlocking(Dispatchers.Default) {
    val filter = McpToolFilter.AllowList(emptySet())

    withConnection(filter = filter) { client, _ ->
      val tools = client.listTools().tools

      // Should have no tools
      assertThat(tools).isEmpty()
    }
  }

  @Test
  fun `filtered tools cannot be called`() = runBlocking(Dispatchers.Default) {
    val filter = McpToolFilter.AllowList(setOf(tool1))

    withConnection(filter = filter) { client, _ ->
      val tools = client.listTools().tools

      // Only read_file should be available
      assertThat(tools).hasSize(1)
      assertThat(tools.first().name).isEqualTo(tool1)

      // Attempting to call a filtered-out tool should fail
      // (The tool won't be registered, so the SDK should return an error result)
      val result = client.callTool(tool2, kotlinx.serialization.json.buildJsonObject {
        put("filePath", kotlinx.serialization.json.JsonPrimitive("test.txt"))
      })

      assertThat(result.isError).isTrue()
      val textContent = result.content.firstOrNull() as? TextContent
      assertThat(textContent).isNotNull()
      assertThat(textContent!!.text).contains("not found")
    }
  }

  private suspend fun withConnection(
    filter: McpToolFilter = McpToolFilter.AllowAll,
    allowedToolsHeader: String? = null,
    action: suspend (Client, List<String>) -> Unit
  ) {
    require(allowedToolsHeader == null || filter == McpToolFilter.AllowAll) {
      "Cannot use both filter and allowedToolsHeader - specify only one"
    }

    if (allowedToolsHeader != null) {
      // Use global server with header-based filtering
      withGlobalServerConnection(allowedToolsHeader) { client ->
        action(client, emptyList())
      }
    } else {
      // Use authorized session with filter-based filtering
      withAuthorizedSessionConnection(filter) { client ->
        action(client, emptyList())
      }
    }
  }

  private suspend fun withConnectionUsingHeader(
    allowedToolsHeader: String,
    action: suspend (Client) -> Unit
  ) {
    withConnection(allowedToolsHeader = allowedToolsHeader) { client, _ ->
      action(client)
    }
  }

  private suspend fun withAuthorizedSessionConnection(
    filter: McpToolFilter,
    action: suspend (Client) -> Unit
  ) {
    val service = McpServerService.getInstance()

    service.authorizedSession(
      McpServerService.McpSessionOptions(
        commandExecutionMode = McpServerService.AskCommandExecutionMode.DONT_ASK,
        toolFilter = filter
      )
    ) { port, tokenName, tokenValue ->
      connectToServer(
        port = port,
        extraHeaders = {
          header(tokenName, tokenValue)
        },
        action = action
      )
    }
  }

  private suspend fun withGlobalServerConnection(
    allowedToolsHeader: String,
    action: suspend (Client) -> Unit
  ) {
    McpServerService.getInstance().start()
    try {
      val port = McpServerService.getInstance().port
      connectToServer(
        port = port,
        extraHeaders = {
          header(IJ_MCP_ALLOWED_TOOLS, allowedToolsHeader)
        },
        action = action
      )
    } finally {
      McpServerService.getInstance().stop()
    }
  }

  private suspend fun connectToServer(
    port: Int,
    extraHeaders: HttpRequestBuilder.() -> Unit,
    action: suspend (Client) -> Unit
  ) {
    val httpClient = HttpClient {
      install(SSE)
    }

    val sseClientTransport = SseClientTransport(httpClient, "http://localhost:$port/sse") {
      header(IJ_MCP_SERVER_PROJECT_PATH, project.basePath)
      extraHeaders()
    }

    val client = Client(Implementation(name = "test client", version = "1.0"))

    try {
      client.connect(sseClientTransport)
      action(client)
    } finally {
      sseClientTransport.close()
    }
  }
}
