package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
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

    val client = CodexClient(McpClientInfo.Scope.GLOBAL, configPath)
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

    val client = CodexClient(McpClientInfo.Scope.GLOBAL, configPath)
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

    val client = TestCodexClient(McpClientInfo.Scope.GLOBAL, configPath, "http://localhost:7777/stream")
    client.configure()

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

    val client = TestCodexClient(McpClientInfo.Scope.GLOBAL, configPath, "http://localhost:8888/stream")
    client.configure()

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
    val client = CodexClient(McpClientInfo.Scope.GLOBAL, configPath)
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

    val client = CodexClient(McpClientInfo.Scope.GLOBAL, configPath)
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

    val client = CodexClient(McpClientInfo.Scope.GLOBAL, configPath)
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

    val client = CodexClient(McpClientInfo.Scope.GLOBAL, configPath)
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

    val client = TestCodexClient(McpClientInfo.Scope.GLOBAL, configPath, "http://localhost:2222/stream")
    client.configure()

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

    val client = TestCodexClient(McpClientInfo.Scope.GLOBAL, configPath, "http://localhost:3333/stream")
    client.configure()

    val result = configPath.readText()
    assertTrue(result.contains("[random.section]"))
    assertTrue(result.contains("value = 42"))
    assertTrue(result.contains("[mcp_servers.codextest]"))
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

    val client = TestCodexClient(McpClientInfo.Scope.GLOBAL, configPath, "http://localhost:4444/stream")
    client.configure()

    val result = configPath.readText()
    assertTrue(result.contains("""[projects."/Users/test/project"]"""))
    assertFalse(result.contains("""[projects.""/Users/test/project""]"""))
  }
}

private class TestCodexClient(
  scope: McpClientInfo.Scope,
  configPath: Path,
  private val fixedUrl: String,
) : CodexClient(scope, configPath) {
  override val streamableHttpUrl: String
    get() = fixedUrl
}
