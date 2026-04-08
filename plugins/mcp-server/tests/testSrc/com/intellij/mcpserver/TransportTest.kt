package com.intellij.mcpserver

import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.asTool
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
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
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class TransportTest {
  companion object {
    val projectFixture = projectFixture(openAfterCreation = true)
    val project by projectFixture

    @JvmStatic
    fun getTransports(): Array<TransportHolder> = arrayOf(
      StdioTransportHolder(project),
      SseTransportHolder(project),
      HttpTransportHolder(project),
    )
  }

  @ParameterizedTest
  @MethodSource("getTransports")
  fun list_tools(transport: TransportHolder) = transportTest(transport) { client ->
    val listTools = client.listTools()
    assert(listTools.tools.isNotEmpty()) { "No tools returned" }
  }

  @ParameterizedTest
  @MethodSource("getTransports")
  fun list_tools_has_title(transport: TransportHolder) = transportTest(transport) { client ->
    delay(500.milliseconds)
    Disposer.newDisposable().use { disposable ->
      application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(object : McpToolsProvider {
        override fun getTools(): List<McpTool> = listOf(this@TransportTest::test_tool.asTool())
      }, disposable)
      delay(500.milliseconds)

      val tool = client.listTools().tools.single { it.name == "test_tool" }
      assertEquals("Test title", tool.title)
    }
    delay(500.milliseconds)
  }

  @ParameterizedTest
  @MethodSource("getTransports")
  fun tool_call_has_project(transport: TransportHolder) = transportTest(transport) { client ->
    delay(500.milliseconds)
    Disposer.newDisposable().use { disposable ->
      application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(object : McpToolsProvider {
        override fun getTools(): List<McpTool> = listOf(this@TransportTest::test_tool.asTool())
      }, disposable)
      // tools change is being listened in a background coroutine, so we have to wait a bit
      delay(500.milliseconds)
      client.callTool("test_tool", emptyMap())

      val actual = withTimeout(2000.milliseconds) { projectFromTool.await() }
      assertThat(actual).isEqualTo(project)
    }
    // the same to unregistration. Otherwise, tools change notification is being sent into a closed transport
    delay(500.milliseconds) // delay for exit from use {}
  }

  val projectFromTool = CompletableDeferred<Project?>()

  @com.intellij.mcpserver.annotations.McpTool(title = "Test title")
  @McpDescription("Test description")
  suspend fun test_tool() {
    projectFromTool.complete(currentCoroutineContext().projectOrNull)
  }

  private fun transportTest(transportHolder: TransportHolder, action: suspend (Client) -> Unit) = runBlocking(Dispatchers.Default) {
    try {
      McpServerService.getInstance().start()
      val client = Client(Implementation(name = "test client", version = "1.0"))
      try {
        client.connect(transportHolder.transport)
      }
      catch (e: Exception) {
        throw AssertionError("Failed to connect to the server: ${e.message}. Additional diagnostics:\r\n${transportHolder.diagnostics}")
      }
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
  open val diagnostics: String = ""

  // do not make it AutoCloseable because Junit tries to close it automatically but we want to close it in test method manually
  open fun close() {
    runBlocking(Dispatchers.Default) {
      transport.close()
    }
  }
}

@Suppress("RAW_SCOPE_CREATION")
class StdioTransportHolder(project: Project) : TransportHolder() {
  private val scope = CoroutineScope(Job())
  private val diagnosticsCollector = StringBuilder()

  val process: Process by lazy {
    val mcpServerCommandLine = createStdioMcpServerCommandLine(McpServerService.getInstance().port, project.basePath)
    val proc = mcpServerCommandLine.toProcessBuilder().start()
    scope.launch {
      proc.errorStream.bufferedReader().use { reader ->
        reader.forEachLine {
          diagnosticsCollector.appendLine(it)
        }
      }
    }
    return@lazy proc
  }

  override val transport: AbstractTransport by lazy {
    StdioClientTransport(process.inputStream.asSource().buffered(), process.outputStream.asSink().buffered())
  }

  override val diagnostics: String
    get() = diagnosticsCollector.toString()

  override fun close() {
    super.close() //sseClientTransport.close()
    scope.cancel()
    if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly()
    if (!process.waitFor(10, TimeUnit.SECONDS)) throw AssertionError("Process is still alive")
  }

  override fun toString(): String = "Stdio"
}

class SseTransportHolder(project: Project) : TransportHolder() {
  override val transport: AbstractTransport by lazy {
    val addressProvider = McpServerConnectionAddressProvider.getInstanceOrNull() ?: throw AssertionError("No address provider")
    val transportUrl = addressProvider.serverSseUrl
    SseClientTransport(HttpClient {
      install(SSE)
    }, transportUrl) {
      project.basePath?.let { header(IJ_MCP_SERVER_PROJECT_PATH, it) }
    }
  }

  override fun toString(): String = "SSE"
}

class HttpTransportHolder(project: Project) : TransportHolder() {
  override val transport: AbstractTransport by lazy {
    val addressProvider = McpServerConnectionAddressProvider.getInstanceOrNull() ?: throw AssertionError("No address provider")
    val transportUrl = addressProvider.serverStreamUrl
    StreamableHttpClientTransport(HttpClient {
      install(SSE)
    }, transportUrl) {
      project.basePath?.let { header(IJ_MCP_SERVER_PROJECT_PATH, it) }
    }
  }

  override fun toString(): String = "Http Stream"
}
