package com.intellij.mcpserver

import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpToolHintValue.FALSE
import com.intellij.mcpserver.annotations.McpToolHintValue.TRUE
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.impl.IJ_MCP_AUTH_TOKEN
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.McpServerServiceImpl
import com.intellij.mcpserver.impl.util.asTool
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.impl.util.projectPathParameterName
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.mcpserver.testFramework.awaitTool
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class TransportTest {
  companion object {
    val projectFixture = projectFixture(openAfterCreation = true)
    val project by projectFixture
    val secondProjectFixture = projectFixture(openAfterCreation = true)
    val secondProject by secondProjectFixture

    @JvmStatic
    fun getTransports(): Array<TransportHolder> = transports { McpServerServiceImpl.getInstance().globalToken }

    @JvmStatic
    fun getTransportsWithoutToken(): Array<TransportHolder> = transports { null }

    @JvmStatic
    fun getTransportsWithWrongToken(): Array<TransportHolder> = transports { "wrong-token" }

    private fun transports(token: () -> String?): Array<TransportHolder> = arrayOf(
      StdioTransportHolder(project, token),
      SseTransportHolder(project, token),
      HttpTransportHolder(project, token),
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
    Disposer.newDisposable().use { disposable ->
      application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(object : McpToolsProvider {
        override fun getTools(): List<McpTool> = listOf(this@TransportTest::test_tool.asTool())
      }, disposable)

      val tool = client.awaitTool("test_tool")
      assertEquals("Test title", tool.title)
    }
  }

  @ParameterizedTest
  @MethodSource("getTransports")
  fun list_tools_has_annotations(transport: TransportHolder) = transportTest(transport) { client ->
    Disposer.newDisposable().use { disposable ->
      application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(object : McpToolsProvider {
        override fun getTools(): List<McpTool> = listOf(this@TransportTest::test_tool.asTool())
      }, disposable)

      val tool = client.awaitTool("test_tool")
      assertThat(tool.annotations?.readOnlyHint).isTrue()
      assertThat(tool.annotations?.openWorldHint).isFalse()
    }
  }

  @ParameterizedTest
  @MethodSource("getTransports")
  fun tool_call_has_project(transport: TransportHolder) = transportTest(transport) { client ->
    Disposer.newDisposable().use { disposable ->
      application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(object : McpToolsProvider {
        override fun getTools(): List<McpTool> = listOf(this@TransportTest::test_tool.asTool())
      }, disposable)
      client.awaitTool("test_tool")
      client.callTool("test_tool", emptyMap())

      val actual = withTimeout(2000.milliseconds) { projectFromTool.await() }
      assertThat(actual).isEqualTo(project)
    }
  }

  @ParameterizedTest
  @MethodSource("getTransports")
  fun tool_call_prefers_project_path_argument_over_session_project(transport: TransportHolder) = transportTest(transport) { client ->
    Disposer.newDisposable().use { disposable ->
      application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(object : McpToolsProvider {
        override fun getTools(): List<McpTool> = listOf(this@TransportTest::test_tool.asTool())
      }, disposable)
      client.awaitTool("test_tool")
      client.callTool("test_tool", mapOf(projectPathParameterName to requireNotNull(secondProject.basePath)))

      val actual = withTimeout(2000.milliseconds) { projectFromTool.await() }
      assertThat(actual).isEqualTo(secondProject)
    }
  }

  @ParameterizedTest
  @MethodSource("getTransports")
  fun list_tools_strips_project_path_when_session_header_present(transport: TransportHolder) = transportTest(transport) { client ->
    val listTools = client.listTools()
    assertThat(listTools.tools).isNotEmpty()
    for ((name, inputSchema) in listTools.tools) {
      val properties = inputSchema.properties
      assertThat(properties?.containsKey(projectPathParameterName))
        .describedAs("Tool '$name' should not have '$projectPathParameterName' in schema when session project path is set")
        .isFalse()
    }
  }

  @ParameterizedTest
  @MethodSource("getTransportsWithoutToken")
  fun connect_without_token(transport: TransportHolder) = rejectionTest(transport)

  @ParameterizedTest
  @MethodSource("getTransportsWithWrongToken")
  fun connect_with_wrong_token(transport: TransportHolder) = rejectionTest(transport)

  private fun rejectionTest(transport: TransportHolder) = runBlocking(Dispatchers.Default) { assertConnectRejected(transport) }

  @Test
  fun private_session_token_is_rejected(): Unit = runBlocking(Dispatchers.Default) {
    val options =
      McpServerService.McpSessionOptions(commandExecutionMode = McpServerService.AskCommandExecutionMode.RESPECT_GLOBAL_SETTINGS)
    McpServerService.getInstance().authorizedSession(options) { _, _, sessionToken ->
      for (transport in transports { sessionToken }) assertConnectRejected(transport)
    }
  }

  private suspend fun assertConnectRejected(transportHolder: TransportHolder) {
    McpServerService.getInstance().start()
    try {
      val client = Client(Implementation(name = "test client", version = "1.0"))
      val failure = runCatching { withTimeout(10.seconds) { client.connect(transportHolder.transport) } }.exceptionOrNull()
      assertThat(failure).describedAs("connect over $transportHolder").isNotNull().isNotInstanceOf(TimeoutCancellationException::class.java)
    }
    finally {
      transportHolder.close()
      McpServerService.getInstance().stop()
    }
  }

  val projectFromTool = CompletableDeferred<Project?>()

  @com.intellij.mcpserver.annotations.McpTool(title = "Test title")
  @McpToolHints(readOnlyHint = TRUE, openWorldHint = FALSE)
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

abstract class TransportHolder(private val token: () -> String?) {
  abstract val transport: AbstractTransport
  open val diagnostics: String = ""
  protected fun authToken(): Pair<String, String>? = token()?.let { IJ_MCP_AUTH_TOKEN to it }

  // do not make it AutoCloseable because Junit tries to close it automatically but we want to close it in test method manually
  open fun close() {
    runBlocking(Dispatchers.Default) {
      transport.close()
    }
  }
}

@Suppress("RAW_SCOPE_CREATION")
class StdioTransportHolder(project: Project, token: () -> String?) : TransportHolder(token) {
  private val scope = CoroutineScope(Job())
  private val diagnosticsCollector = StringBuilder()

  val process: Process by lazy {
    val mcpServerCommandLine = createStdioMcpServerCommandLine(McpServerService.getInstance().port,
                                                               project.basePath,
                                                               authToken())
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

class SseTransportHolder(project: Project, token: () -> String?) : TransportHolder(token) {
  private val httpClient = HttpClient {
    install(SSE)
  }

  override val transport: AbstractTransport by lazy {
    val addressProvider = McpServerConnectionAddressProvider.getInstanceOrNull() ?: throw AssertionError("No address provider")
    val transportUrl = addressProvider.serverSseUrl
    SseClientTransport(httpClient, transportUrl) {
      project.basePath?.let {
        header(IJ_MCP_SERVER_PROJECT_PATH, it)
      }
      authToken()?.let { [name, value] -> header(name, value) }
    }
  }

  override fun close() {
    httpClient.use {
      super.close()
    }
  }

  override fun toString(): String = "SSE"
}

class HttpTransportHolder(project: Project, token: () -> String?) : TransportHolder(token) {
  private val httpClient = HttpClient {
    install(SSE)
  }

  override val transport: AbstractTransport by lazy {
    val addressProvider = McpServerConnectionAddressProvider.getInstanceOrNull() ?: throw AssertionError("No address provider")
    val transportUrl = addressProvider.serverStreamUrl
    StreamableHttpClientTransport(httpClient, transportUrl) {
      project.basePath?.let { header(IJ_MCP_SERVER_PROJECT_PATH, it) }
      authToken()?.let { [name, value] -> header(name, value) }
    }
  }

  override fun close() {
    httpClient.use {
      super.close()
    }
  }

  override fun toString(): String = "Http Stream"
}
