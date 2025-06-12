package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.test.runTest
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.fail

@TestApplication
class StdioRunnerTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun list_tools_stdio_runner() = runTest {
    McpServerService.getInstance().start()
    Disposer.register(disposable, Disposable {
      McpServerService.getInstance().stop()
    })
    val mcpServerCommandLine = createStdioMcpServerCommandLine(McpServerService.getInstance().port, null)
    val processBuilder = mcpServerCommandLine.toProcessBuilder()

      val process = processBuilder.start()
      val stdioClientTransport = StdioClientTransport(process.inputStream.asInput(), process.outputStream.asSink().buffered())
      val client = Client(Implementation(name = "test client", version = "1.0"))
      client.connect(stdioClientTransport)

      val listTools = client.listTools() ?: fail("No tools returned")
      assert(listTools.tools.isNotEmpty()) { "No tools returned" }

      stdioClientTransport.close()
      if  (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
      if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) fail("Process is still alive")
  }

  @Test
  fun test_createStdioMcpServerJsonConfiguration() {
    val port = 12345
    val projectPath = "/test/project/path"
    
    val jsonConfig = createStdioMcpServerJsonConfiguration(port, projectPath)
    
    // Verify the JSON structure
    Assertions.assertNotNull(jsonConfig["command"])
    Assertions.assertNotNull(jsonConfig["args"])
    Assertions.assertNotNull(jsonConfig["env"])
    
    // Verify command is a java executable
    val command = jsonConfig["command"]?.jsonPrimitive?.content
    assertTrue(command?.endsWith("java") == true, "Command should be java executable")
    
    // Verify environment variables
    val env = jsonConfig["env"]?.jsonObject!!
    Assertions.assertNotNull(env)
    Assertions.assertEquals(port.toString(), env[IJ_MCP_SERVER_PORT]?.jsonPrimitive?.content)
    Assertions.assertEquals(projectPath, env[IJ_MCP_SERVER_PROJECT_PATH]?.jsonPrimitive?.content)
  }

  @Test
  fun test_createStdioMcpServerJsonConfiguration_withoutProjectPath() {
    val port = 12345
    
    val jsonConfig = createStdioMcpServerJsonConfiguration(port, null)
    
    // Verify the JSON structure
    Assertions.assertNotNull(jsonConfig["command"])
    Assertions.assertNotNull(jsonConfig["args"])
    Assertions.assertNotNull(jsonConfig["env"])
    
    // Verify environment variables
    val env = jsonConfig["env"]?.jsonObject!!
    Assertions.assertNotNull(env)
    Assertions.assertEquals(port.toString(), env[IJ_MCP_SERVER_PORT]?.jsonPrimitive?.content)
    // Project path should not be present when null
    assertTrue(env[IJ_MCP_SERVER_PROJECT_PATH] == null)
  }

  @Test
  fun test_createSseServerJsonEntry() {
    val port = 8080
    
    val jsonConfig = createSseServerJsonEntry(port)
    
    // Verify the JSON structure
    Assertions.assertEquals("sse", jsonConfig["type"]?.jsonPrimitive?.content)
    Assertions.assertEquals("http://localhost:$port/sse", jsonConfig["url"]?.jsonPrimitive?.content)
  }
}