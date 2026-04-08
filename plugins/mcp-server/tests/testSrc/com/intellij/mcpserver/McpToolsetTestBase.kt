@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.header
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.platform.commons.annotation.Testable
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively

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
   */
  protected suspend fun <T> withConnection(action: suspend (Client) -> T) {
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
      val client = Client(Implementation(name = "test client", version = "1.0"))

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
