package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClient.Companion.TransportType
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.CodexStreamableHttpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class CodexClientTest {
  @TempDir
  lateinit var tempDir: Path

  @AfterEach
  fun resetOverrides() {
    McpClient.overrideProductSpecificServerKeyForTests(null)
    McpClient.overrideWriteLegacyForTests(null)
  }

  @Test
  fun `isConfigured returns true for stream url`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.test]
      url = "http://localhost:8123/stream"

      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    assertTrue(client.isConfigured() == true)
  }

  @Test
  fun `isConfigured accepts loopback ip`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.test]
      url = "http://127.0.0.1:8123/stream"

      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    assertTrue(client.isConfigured() == true)
  }

  @Test
  fun `configure drops legacy section when legacy disabled`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.jetbrains]
      url = "http://localhost:9999/stream"

      [mcp_servers.preserve]
      url = "keep"

      """.trimIndent()
    )

    McpClient.overrideProductSpecificServerKeyForTests("codextest")
    McpClient.overrideWriteLegacyForTests(false)

    val client = TestCodexClient(McpClientInfo.Scope.Global, configPath, "http://localhost:7777/stream")
    runBlocking(Dispatchers.Default) {
      client.configure(client.getStreamableHttpConfig())
    }

    val result = configPath.readText()
    assertTrue(result.contains("[mcp_servers.codextest]"))
    assertTrue(result.contains("""url = "http://localhost:7777/stream""""))
    assertFalse(result.contains("[mcp_servers.jetbrains]"))
    assertTrue(result.contains("[mcp_servers.preserve]"))
  }

  @Test
  fun `configure does not add legacy section even when legacy enabled`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText("")

    McpClient.overrideProductSpecificServerKeyForTests("codextest")
    McpClient.overrideWriteLegacyForTests(true)

    val client = TestCodexClient(McpClientInfo.Scope.Global, configPath, "http://localhost:8888/stream")
    runBlocking(Dispatchers.Default) {
      client.configure(client.getStreamableHttpConfig())
    }

    val result = configPath.readText()
    assertTrue(result.contains("[mcp_servers.codextest]"))
    assertFalse(result.contains("[mcp_servers.jetbrains]"))
    val expectedUrl = """url = "http://localhost:8888/stream""""
    val occurrences = result.split(expectedUrl).size - 1
    assertTrue(1 == occurrences)
  }

  @Test
  fun `isConfigured returns false when config file does not exist`() {
    val configPath = tempDir.resolve("missing.toml")
    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    assertFalse(client.isConfigured() == true)
  }

  @Test
  fun `isConfigured returns false for non-stream url`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.test]
      url = "http://localhost:8123/api"
      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    assertFalse(client.isConfigured() == true)
  }

  @Test
  fun `isConfigured returns false for malformed url`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.test]
      url = "not-a-url"
      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    assertFalse(client.isConfigured() == true)
  }

  @Test
  fun `isConfigured returns false when mcp_servers section is missing`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [other_section]
      url = "http://localhost:8123/stream"
      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    assertFalse(client.isConfigured() == true)
  }

  @Test
  fun `configure overwrites existing product-specific section`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.codextest]
      url = "http://localhost:1111/stream"
      """.trimIndent()
    )

    McpClient.overrideProductSpecificServerKeyForTests("codextest")
    McpClient.overrideWriteLegacyForTests(false)

    val client = TestCodexClient(McpClientInfo.Scope.Global, configPath, "http://localhost:2222/stream")
    runBlocking(Dispatchers.Default) {
      client.configure(client.getStreamableHttpConfig())
    }

    val result = configPath.readText()
    assertTrue(result.contains("""url = "http://localhost:2222/stream""""))
    assertFalse(result.contains("""url = "http://localhost:1111/stream""""))
  }

  @Test
  fun `configure preserves unrelated content`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      # comment
      [random.section]
      value = 42
      """.trimIndent()
    )

    McpClient.overrideProductSpecificServerKeyForTests("codextest")
    McpClient.overrideWriteLegacyForTests(false)

    val client = TestCodexClient(McpClientInfo.Scope.Global, configPath, "http://localhost:3333/stream")
    runBlocking(Dispatchers.Default) {
      client.configure(client.getStreamableHttpConfig())
    }

    val result = configPath.readText()
    assertTrue(result.contains("[random.section]"))
    assertTrue(result.contains("value = 42"))
    assertTrue(result.contains("[mcp_servers.codextest]"))
  }

  @Test
  fun `configure creates missing codex directory for project config`() {
    val configPath = tempDir.resolve("project").resolve(".codex").resolve("config.toml")

    McpClient.overrideProductSpecificServerKeyForTests("codextest")

    val projectPath = tempDir.resolve("project").toString()
    val client = TestCodexClient(McpClientInfo.Scope.Project(projectPath), configPath, "http://localhost:3333/stream")
    runBlocking(Dispatchers.Default) {
      client.configure(client.getStreamableHttpConfig())
    }

    assertTrue(configPath.parent.exists())
    assertTrue(configPath.exists())
    assertEquals("http://localhost:3333/stream", client.readMcpServersForTest()?.get("codextest")?.url)
  }

  @Test
  fun `configure keeps quoted project keys without doubling quotes`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [projects."/Users/test/project"]
      trust_level = "trusted"

      [mcp_servers.codextest]
      url = "http://localhost:1111/stream"
      """.trimIndent()
    )

    McpClient.overrideProductSpecificServerKeyForTests("codextest")
    McpClient.overrideWriteLegacyForTests(false)

    val client = TestCodexClient(McpClientInfo.Scope.Global, configPath, "http://localhost:4444/stream")
    runBlocking(Dispatchers.Default) {
      client.configure(client.getStreamableHttpConfig())
    }

    val result = configPath.readText()
    assertTrue(result.contains("""[projects."/Users/test/project"]"""))
    assertFalse(result.contains("""[projects.""/Users/test/project""]"""))
  }

  @Test
  fun `getConfiguredTransportTypes detects HTTP Stream`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.test]
      url = "http://localhost:8123/stream"
      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    val types = client.getConfiguredTransportTypes()

    assertTrue(types.contains(TransportType.STREAMABLE_HTTP))
  }

  @Test
  fun `getConfiguredTransportTypes detects Stdio`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.test]
      command = "java"
      args = ["com.intellij.mcpserver.stdio.MainKt"]

      [mcp_servers.test.env]
      IJ_MCP_SERVER_PORT = "8080"
      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    val types = client.getConfiguredTransportTypes()

    assertTrue(types.contains(TransportType.STDIO))
  }

  @Test
  fun `getTransportTypesDisplayString returns HTTP Stream`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.test]
      url = "http://localhost:8123/stream"
      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    val displayString = client.getTransportTypesDisplayString()!!

    assertEquals("HTTP Stream", displayString)
  }

  @Test
  fun `autoConfigure with HTTP Stream succeeds`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText("")

    McpClient.overrideProductSpecificServerKeyForTests("codextest")

    val client = TestCodexClient(McpClientInfo.Scope.Global, configPath, "http://localhost:5555/stream")
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val result = configPath.readText()
    assertTrue(result.contains("[mcp_servers.codextest]"))
    assertTrue(result.contains("""url = "http://localhost:5555/stream""""))
  }

  @Test
  fun `autoConfigure preserves unrelated sections`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [projects."/Users/test/project"]
      trust_level = "trusted"

      [random.section]
      value = 42
      """.trimIndent()
    )

    McpClient.overrideProductSpecificServerKeyForTests("codextest")

    val client = TestCodexClient(McpClientInfo.Scope.Global, configPath, "http://localhost:5555/stream")
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val result = configPath.readText()
    assertTrue(result.contains("""[projects."/Users/test/project"]"""))
    assertTrue(result.contains("[random.section]"))
    assertTrue(result.contains("value = 42"))
    assertTrue(result.contains("[mcp_servers.codextest]"))
  }

  @Test
  fun `configure with headers produces TOML headers sub-table`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText("")

    McpClient.overrideProductSpecificServerKeyForTests("codextest")
    McpClient.overrideWriteLegacyForTests(false)

    val client = TestCodexClient(McpClientInfo.Scope.Global, configPath, "http://localhost:5555/stream")
    runBlocking(Dispatchers.Default) {
      client.configure(
        CodexStreamableHttpConfig(
          url = "http://localhost:5555/stream",
          headers = mapOf("IJ_MCP_SERVER_PROJECT_PATH" to "/my/project")
        )
      )
    }

    val result = configPath.readText()
    assertTrue(result.contains("[mcp_servers.codextest]"))
    assertTrue(result.contains("""url = "http://localhost:5555/stream""""))
    assertTrue(result.contains("IJ_MCP_SERVER_PROJECT_PATH"))
    assertTrue(result.contains(""""/my/project""""))
  }
}

private class TestCodexClient(
  scope: McpClientInfo.Scope,
  configPath: Path,
  private val fixedUrl: String,
) : CodexClient(scope, configPath) {
  override val streamableHttpUrl: String
    get() = fixedUrl

  fun readMcpServersForTest() = readMcpServers()
}
