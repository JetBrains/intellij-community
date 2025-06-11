package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.testFramework.junit5.TestApplication
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.test.runTest
import kotlinx.io.asSink
import kotlinx.io.buffered
import org.junit.jupiter.api.Test
import kotlin.test.fail

@TestApplication
class StdioRunnerTest {

  @Test
  fun list_tools_stdio_runner() = runTest {
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
}