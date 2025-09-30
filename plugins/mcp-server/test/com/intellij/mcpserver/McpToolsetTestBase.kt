@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.CallToolResultBase
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.platform.commons.annotation.Testable
import kotlin.io.path.Path
import kotlin.test.fail

@Testable
@TestApplication
abstract class McpToolsetTestBase {
  companion object {
    @BeforeAll
    @JvmStatic
    fun init() {
      System.setProperty("java.awt.headless", "false")
    }
  }

  protected val projectFixture = projectFixture(openAfterCreation = true)
  protected val project by projectFixture
  protected val moduleFixture = projectFixture.moduleFixture("testModule")
  protected val sourceRootFixture = moduleFixture.sourceRootFixture(pathFixture = projectFixture.pathInProjectFixture(Path("src")))
  // TODO: no idea how to create a file in a subfolder
  protected val mainJavaFileFixture = sourceRootFixture.virtualFileFixture("Main.java", "Main.java content")
  protected val classJavaFileFixture = sourceRootFixture.virtualFileFixture("Class.java", "Class.java content")
  protected val testJavaFileFixture = sourceRootFixture.virtualFileFixture("Test.java", "Test.java content")
  protected val mainJavaFile by mainJavaFileFixture
  protected val classJavaFile by classJavaFileFixture
  protected val testJavaFile by testJavaFileFixture



  protected suspend fun withConnection(action: suspend (Client) -> Unit) {
    McpServerService.getInstance().start()
    // Get the port from McpServerService
    val port = McpServerService.getInstance().port

    // Create HttpClient with SSE support
    val httpClient = HttpClient {
      install(SSE)
    }

    // Create SseClientTransport
    val sseClientTransport = SseClientTransport(httpClient, "http://localhost:$port/sse", requestBuilder = {
      header(IJ_MCP_SERVER_PROJECT_PATH, project.basePath)
    })

    // Create client
    val client = Client(Implementation(name = "test client", version = "1.0"))

    try { // Connect to the server
      client.connect(sseClientTransport)

      action(client)
    }
    finally { // Close the connection
      sseClientTransport.close()
      McpServerService.getInstance().stop()
    }
  }

  protected val CallToolResultBase.textContent: TextContent get() = content.firstOrNull() as? TextContent
                                                                    ?: fail("Tool call result should be TextContent")
  protected suspend fun testMcpTool(
    toolName: String,
    input: kotlinx.serialization.json.JsonObject,
    output: String,
  ) {
    testMcpTool(toolName, input) { result ->
      val textContent = result.textContent
      assertEquals(output, textContent.text, "Tool call result should match expected output")
    }
  }


  protected suspend fun testMcpTool(
    toolName: String,
    input: kotlinx.serialization.json.JsonObject,
    resultChecker: (CallToolResultBase) -> Unit,
  ) {
    withConnection { client ->
      // Call the tool with the provided input
      try {
        McpServerSettings.getInstance().state.enableBraveMode = true
        val result = client.callTool(toolName, input) ?: fail("Tool call result should not be null")
        resultChecker(result)
        // Just verify that the call doesn't throw an exception
        assertNotNull(result, "Tool call result should not be null")
        // Log the result for debugging
        println("[DEBUG_LOG] Tool $toolName result: $result")
      }
      finally {
        McpServerSettings.getInstance().state.enableBraveMode = false
      }
    }
  }
}