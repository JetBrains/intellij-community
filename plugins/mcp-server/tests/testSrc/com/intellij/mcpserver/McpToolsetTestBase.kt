@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.testFramework.McpServerTestExtension
import com.intellij.mcpserver.testFramework.ToolCallWithProgress
import com.intellij.mcpserver.testFramework.mcpServerProjectFixture
import com.intellij.mcpserver.testFramework.textContentOrThrow
import com.intellij.mcpserver.testFramework.withMcpServerConnection
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.platform.commons.annotation.Testable
import java.nio.file.Path
import kotlin.reflect.KFunction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import com.intellij.mcpserver.testFramework.callToolWithProgress as callToolWithProgressShared
import com.intellij.mcpserver.testFramework.testMcpTool as testMcpToolShared
import com.intellij.mcpserver.testFramework.withRegisteredTestTools as withRegisteredTestToolsShared

@Testable
@TestApplication
@ExtendWith(McpServerTestExtension::class)
abstract class McpToolsetTestBase {
  /**
   * Path to the immutable test data dir.
   * If specified, its content will be copied into the opened temp project.
   *
   * Relative values are treated as regular filesystem paths relative to the repository root.
   */
  protected open fun projectTestData(): Path? = null

  /**
   * Opened temp project used by MCP tool tests.
   */
  protected val projectFixture: TestFixture<Project> = mcpServerProjectFixture(projectTestData())

  /**
   * Convenient access to the opened project instance.
   */
  protected val project by projectFixture

  /**
   * Runs the provided MCP client action inside an authorized MCP session bound to [project].
   *
   * Pass a customized [client] with some capabilities to override the default plain client.
   */
  protected suspend fun withConnection(
    client: Client = Client(Implementation(name = "test client", version = "1.0")),
    action: suspend (Client) -> Unit,
  ) {
    withMcpServerConnection(project, client, action = action)
  }

  /**
   * Extracts text payload from a tool response and fails fast when the tool returned a different content kind.
   */
  protected val CallToolResult.textContent: TextContent get() = textContentOrThrow()

  protected suspend fun <T> withRegisteredTestTools(vararg toolFunctions: KFunction<*>, action: suspend () -> T): T =
    withRegisteredTestToolsShared(*toolFunctions, action = action)

  protected suspend fun callToolWithProgress(
    toolName: String,
    arguments: Map<String, Any?> = emptyMap(),
    meta: Map<String, Any?> = emptyMap(),
    timeout: Duration = 239.seconds,
  ): ToolCallWithProgress = callToolWithProgressShared(project, toolName, arguments, meta, timeout)

  /**
   * Calls an MCP tool and checks that the textual result matches [output] exactly.
   */
  protected suspend fun testMcpTool(
    toolName: String,
    input: JsonObject,
    output: String,
  ) {
    testMcpToolShared(project, toolName, input, output)
  }

  /**
   * Calls an MCP tool and delegates response validation to [resultChecker].
   */
  protected suspend fun testMcpTool(
    toolName: String,
    input: JsonObject,
    resultChecker: (CallToolResult) -> Unit,
  ) {
    testMcpToolShared(project, toolName, input) { result -> resultChecker(result) }
  }
}
