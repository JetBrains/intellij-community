@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.stdio.IJ_MCP_ALLOWED_TOOLS
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import io.kotest.common.runBlocking
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable
import kotlin.test.DefaultAsserter.assertNotNull

@Testable
@TestApplication
class McpToolFilterIntegrationTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project by projectFixture
  private val moduleFixture = projectFixture.moduleFixture("testModule")

  private val tool1 = "list_directory_tree"
  private val tool2 = "open_file_in_editor"
  private val tool3 = "get_file_text_by_path"

  @Test
  fun `server exposes all tools with AllowAll filter`() = runBlocking {
    withConnection(filter = McpToolFilter.AllowAll) { client, _ ->
      val tools = client.listTools().tools

      // Should have all available tools
      assertTrue(tools.size >= 10, "Should expose multiple tools with AllowAll filter")

      // Verify some common tools are present
      assertTrue(tools.any { it.name == tool1 }, "Should have $tool1 tool")
      assertTrue(tools.any { it.name == tool2 }, "Should have $tool2 tool")
    }
  }


  @Test
  fun `server filters tools with AllowList filter`() = runBlocking {
    val allowedTools = setOf(tool1, tool2)
    val filter = McpToolFilter.AllowList(allowedTools)

    withConnection(filter = filter) { client, _ ->
      val tools = client.listTools().tools

      // Should only have allowed tools
      assertEquals(2, tools.size, "Should only expose allowed tools")

      // Verify only allowed tools are present
      val toolNames = tools.map { it.name }.toSet()
      assertEquals(allowedTools, toolNames, "Should only have allowed tools")

      // Verify filtered tools are not present
      assertFalse(tools.any { it.name == tool3 }, "Should not have git_commit")
    }
  }

  @Test
  fun `server filters tools with header-based filter`() = runBlocking {
    val allowedToolsHeader = "$tool1,$tool2,$tool3"

    withConnectionUsingHeader(allowedToolsHeader = allowedToolsHeader) { client ->
      val tools = client.listTools().tools

      // Should only have tools from header
      assertEquals(3, tools.size, "Should only expose tools from header")

      val toolNames = tools.map { it.name }.toSet()
      assertEquals(setOf(tool1, tool2, tool3), toolNames)
    }
  }

  @Test
  fun `server handles malformed header gracefully`() = runBlocking {
    // Test with spaces and empty entries
    val allowedToolsHeader = " $tool1 , , $tool2 , "

    withConnectionUsingHeader(allowedToolsHeader = allowedToolsHeader) { client ->
      val tools = client.listTools().tools

      // Should filter out empty entries and trim spaces
      assertEquals(2, tools.size, "Should handle malformed header")

      val toolNames = tools.map { it.name }.toSet()
      assertEquals(setOf(tool1, tool2), toolNames)
    }
  }

  @Test
  fun `server exposes no tools with empty AllowList`() = runBlocking {
    val filter = McpToolFilter.AllowList(emptySet())

    withConnection(filter = filter) { client, _ ->
      val tools = client.listTools().tools

      // Should have no tools
      assertEquals(0, tools.size, "Should expose no tools with empty allow list")
    }
  }

  @Test
  fun `filtered tools cannot be called`() = runBlocking {
    val filter = McpToolFilter.AllowList(setOf(tool1))

    withConnection(filter = filter) { client, _ ->
      val tools = client.listTools().tools

      // Only read_file should be available
      assertEquals(1, tools.size)
      assertEquals(tool1, tools[0].name)

      // Attempting to call a filtered-out tool should fail
      // (The tool won't be registered, so the SDK should return an error)
      val result = try {
        client.callTool(tool2, kotlinx.serialization.json.buildJsonObject {
          put("filePath", kotlinx.serialization.json.JsonPrimitive("test.txt"))
        })
        null
      } catch (_: Exception) {
        "error" // Expected to fail
      }

      assertNotNull(result, "Calling filtered tool should fail")
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
