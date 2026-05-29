@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.asTool
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.application
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Progress
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.platform.commons.annotation.Testable
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.reflect.KFunction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Testable
@TestApplication
abstract class McpToolsetTestBase {
  companion object {
    private val TEST_TOOLS_UPDATE_DELAY = 500.milliseconds

    @BeforeAll
    @JvmStatic
    fun init() {
      System.setProperty("java.awt.headless", "false")
    }
  }

  /**
   * Path to the immutable test data dir.
   * If specified, its content will be copied into the opened temp project.
   *
   * Relative values are treated as regular filesystem paths relative to the repository root.
   */
  protected open fun projectTestData(): Path? = null

  @OptIn(ExperimentalPathApi::class)
  private fun projectPathFixture(): TestFixture<Path> = testFixture {
    val path = tempPathFixture().init()
    projectTestData()?.let { resolveRelativelyToRoot(it).copyToRecursively(path, followLinks = false, overwrite = true) }
    initialized(path) { }
  }

  /**
   * Opened temp project used by MCP tool tests.
   */
  protected val projectFixture: TestFixture<Project> = projectFixture(
      pathFixture = projectPathFixture(),
      openProjectTask = com.intellij.ide.impl.OpenProjectTask { createModule = false },
      openAfterCreation = true,
    )

  /**
   * Convenient access to the opened project instance.
   */
  protected val project by projectFixture

  @BeforeEach
  fun prepareProject() {
    project.basePath?.let { Path.of(it).refreshAndFindVirtualFileOrDirectory()?.refresh(false, true) }
    DumbService.getInstance(project).waitForSmartMode()
  }



  private fun resolveRelativelyToRoot(path: Path): Path {
    if (path.isAbsolute) return path

    val repoRoot = PathManager.getHomeDirFor(javaClass)
                   ?: error("Cannot resolve repository root for ${javaClass.name}")
    return repoRoot.resolve(path)
  }

  /**
   * Runs the provided MCP client action inside an authorized MCP session bound to [project].
   *
   * Pass a customized [client] with some capabilities to override the default plain client.
   */
  protected suspend fun <T> withConnection(
    client: Client = Client(Implementation(name = "test client", version = "1.0")),
    action: suspend (Client) -> T,
  ) {
    var result: Result<T>? = null
    val projectBasePath = project.basePath

    McpServerService.getInstance().authorizedSession(
      McpServerService.McpSessionOptions(
        commandExecutionMode = McpServerService.AskCommandExecutionMode.DONT_ASK,
      ),
    ) { port, authTokenName, authTokenValue ->
      val addressProvider = McpServerConnectionAddressProvider.getInstanceOrNull()
      val transportUrl = addressProvider?.httpUrl("/stream", portOverride = port)
                         ?: "http://localhost:$port/stream"
      val httpClient = HttpClient {
        install(SSE)
      }
      val transport = StreamableHttpClientTransport(httpClient, transportUrl, requestBuilder = {
        projectBasePath?.let { header(IJ_MCP_SERVER_PROJECT_PATH, it) }
        header(authTokenName, authTokenValue)
      })

      try {
        client.connect(transport)
        result = runCatching {
          action(client)
        }
      }
      finally {
        transport.close()
        httpClient.close()
      }
    }

    result?.getOrThrow() ?: error("Authorized MCP session finished without producing a client result")
  }

  /**
   * Extracts text payload from a tool response and fails fast when the tool returned a different
   * content kind.
   */
  protected val CallToolResult.textContent: TextContent get() = content.firstOrNull() as? TextContent
                                                                 ?: throw AssertionError("Tool call result should be TextContent")

  protected data class ObservedProgress(
    val progress: Progress,
    val receivedAtNanos: Long,
  )

  protected data class ToolCallWithProgress(
    val result: CallToolResult,
    val progressEvents: List<ObservedProgress>,
  )

  protected suspend fun <T> withRegisteredTestTools(vararg toolFunctions: KFunction<*>, action: suspend () -> T): T {
    var result: T? = null
    Disposer.newDisposable().use { disposable ->
      application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(
        object : McpToolsProvider {
          override fun getTools(): List<McpTool> = toolFunctions.map { toolFunction -> toolFunction.asTool() }
        },
        disposable
      )
      delay(TEST_TOOLS_UPDATE_DELAY)
      result = action()
      delay(TEST_TOOLS_UPDATE_DELAY)
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  protected suspend fun callToolWithProgress(
    toolName: String,
    arguments: Map<String, Any?> = emptyMap(),
    meta: Map<String, Any?> = emptyMap(),
    timeout: Duration = 10.seconds,
  ): ToolCallWithProgress {
    val progressEvents = ArrayList<ObservedProgress>()
    var result: CallToolResult? = null
    withConnection { client ->
      result = client.callTool(
        name = toolName,
        arguments = arguments,
        meta = meta,
        options = RequestOptions(
          onProgress = { progress ->
            progressEvents.add(ObservedProgress(progress = progress, receivedAtNanos = System.nanoTime()))
          },
          timeout = timeout,
        ),
      )
    }
    return ToolCallWithProgress(
      result = requireNotNull(result),
      progressEvents = progressEvents,
    )
  }

  /**
   * Calls an MCP tool and checks that the textual result matches [output] exactly.
   */
  protected suspend fun testMcpTool(
    toolName: String,
    input: kotlinx.serialization.json.JsonObject,
    output: String,
  ) {
    testMcpTool(toolName, input) { result ->
      val textContent = result.textContent
      assertThat(textContent.text).isEqualTo(output)
    }
  }


  /**
   * Calls an MCP tool and delegates response validation to [resultChecker].
   */
  protected suspend fun testMcpTool(
    toolName: String,
    input: kotlinx.serialization.json.JsonObject,
    resultChecker: (CallToolResult) -> Unit,
  ) {
    withConnection { client ->
      val result = client.callTool(toolName, input)
      resultChecker(result)
      assertThat(result).isNotNull()
      println("[DEBUG_LOG] Tool $toolName result: $result")
    }
  }
}
