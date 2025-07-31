package com.intellij.mcpserver

import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.asTool
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail

@TestApplication
class TransportTest {

  companion object {
    val projectFixture = projectFixture(openAfterCreation = true)
    val project by projectFixture

    @JvmStatic
    fun getTransports(): Array<TransportHolder> {
      return arrayOf(
        StdioTransportHolder(project),
        SseTransportHolder(project),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("getTransports")
  fun list_tools(transport: TransportHolder) = transportTest(transport) { client ->
    val listTools = client.listTools() ?: fail("No tools returned")
    assert(listTools.tools.isNotEmpty()) { "No tools returned" }
  }

  @Test
  fun tool_call_has_project_stdio() = tool_call_has_project(StdioTransportHolder(project))

  @Test
  fun tool_call_has_project_sse() = tool_call_has_project(StdioTransportHolder(project))

  fun tool_call_has_project(transport: TransportHolder) = transportTest(transport) { client ->
    Disposer.newDisposable().use { disposable ->
      application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(object : McpToolsProvider {
        override fun getTools(): List<McpTool> {
          return listOf(this@TransportTest::test_tool.asTool())
        }
      }, disposable)
      client.callTool("test_tool", emptyMap())

      val actual = withTimeout(2000) { projectFromTool.await() }
      assertEquals(project, actual)
    }
  }

  val projectFromTool = CompletableDeferred<Project?>()

  @com.intellij.mcpserver.annotations.McpTool()
  @McpDescription("Test description")
  suspend fun test_tool() {
    projectFromTool.complete(currentCoroutineContext().getProjectOrNull(lookForAnyProject = false))
  }

  private fun transportTest(transportHolder: TransportHolder, action: suspend (Client) -> Unit) = runBlocking {
    try {
      McpServerService.getInstance().start()
      val client = Client(Implementation(name = "test client", version = "1.0"))
      client.connect(transportHolder.transport)
      action(client)
    }
    finally {
      transportHolder.close()
      McpServerService.getInstance().stop()
    }
  }
}

abstract class TransportHolder {
  abstract val transport: AbstractTransport

  // do not make it AutoCloseable because Junit tries to close it automatically but we want to close it in test method manually
  open fun close() {
    runBlocking {
      transport.close()
    }
  }
}

class StdioTransportHolder(project: Project) : TransportHolder() {
  val process: Process by lazy {
    createStdioMcpServerCommandLine(McpServerService.getInstance().port, project.basePath).toProcessBuilder().start()
  }

  override val transport: AbstractTransport by lazy {
    StdioClientTransport(process.inputStream.asSource().buffered(), process.outputStream.asSink().buffered())
  }

  override fun close() {
    super.close() //sseClientTransport.close()
    if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
    if (!process.waitFor(10, TimeUnit.SECONDS)) fail("Process is still alive")
  }

  override fun toString(): String {
    return "Stdio"
  }
}

class SseTransportHolder(project: Project) : TransportHolder() {
  override val transport: AbstractTransport by lazy {
    SseClientTransport(HttpClient {
      install(SSE)
    }, "http://localhost:${McpServerService.getInstance().port}/sse") {
      header(IJ_MCP_SERVER_PROJECT_PATH, project.basePath)
    }
  }

  override fun toString(): String {
    return "SSE"
  }
}