package com.intellij.mcpserver.clients

import com.intellij.mcpserver.clients.McpClient.Companion.TransportType
import com.intellij.mcpserver.clients.impl.ClaudeCodeClient
import com.intellij.mcpserver.clients.impl.CodexClient
import com.intellij.mcpserver.clients.impl.CursorClient
import com.intellij.mcpserver.clients.impl.WindsurfClient
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Tests for MCP client transport type detection and display formatting.
 * 
 * Tests verify:
 * - getConfiguredTransportTypes() correctly detects Stdio, SSE, and HTTP Stream
 * - Multiple transports can be detected simultaneously
 * - Unrelated server entries are ignored
 * - getTransportTypesDisplayString() formats transport types correctly
 */
@TestApplication
class McpClientTransportDetectionTest {
  @TempDir
  lateinit var tempDir: Path

  @AfterEach
  fun resetOverrides() {
    McpClient.overrideProductSpecificServerKeyForTests(null)
    McpClient.overrideWriteLegacyForTests(null)
  }

  @Test
  fun `getConfiguredTransportTypes detects Stdio in JSON config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "mcpServers": {
          "test": {
            "command": "java",
            "args": ["com.intellij.mcpserver.stdio.MainKt"],
            "env": {
              "IJ_MCP_SERVER_PORT": "8080"
            }
          }
        }
      }
      """.trimIndent()
    )

    val client = ClaudeCodeClient(McpClientInfo.Scope.GLOBAL, configPath)
    val types = client.getConfiguredTransportTypes()

    assertEquals(1, types.size)
    assertTrue(types.contains(TransportType.STDIO))
  }

  @Test
  fun `getConfiguredTransportTypes detects SSE in JSON config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "mcpServers": {
          "test": {
            "url": "http://localhost:8123/sse",
            "type": "sse"
          }
        }
      }
      """.trimIndent()
    )

    val client = CursorClient(McpClientInfo.Scope.GLOBAL, configPath)
    val types = client.getConfiguredTransportTypes()

    assertEquals(1, types.size)
    assertTrue(types.contains(TransportType.SSE))
  }

  @Test
  fun `getConfiguredTransportTypes detects HTTP Stream in JSON config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "mcpServers": {
          "test": {
            "url": "http://localhost:8123/stream",
            "type": "http"
          }
        }
      }
      """.trimIndent()
    )

    val client = WindsurfClient(McpClientInfo.Scope.GLOBAL, configPath)
    val types = client.getConfiguredTransportTypes()

    assertEquals(1, types.size)
    assertTrue(types.contains(TransportType.STREAMABLE_HTTP))
  }

  @Test
  fun `getConfiguredTransportTypes detects multiple transports in JSON config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "mcpServers": {
          "test-stdio": {
            "command": "java",
            "args": ["MainKt"]
          },
          "test-sse": {
            "url": "http://localhost:8123/sse",
            "type": "sse"
          },
          "test-http": {
            "url": "http://localhost:8123/stream",
            "type": "http"
          }
        }
      }
      """.trimIndent()
    )

    val client = CursorClient(McpClientInfo.Scope.GLOBAL, configPath)
    val types = client.getConfiguredTransportTypes()

    assertEquals(3, types.size)
    assertTrue(types.contains(TransportType.STDIO))
    assertTrue(types.contains(TransportType.SSE))
    assertTrue(types.contains(TransportType.STREAMABLE_HTTP))
  }

  @Test
  fun `getConfiguredTransportTypes returns empty set for missing config`() {
    val configPath = tempDir.resolve("missing.json")

    val client = ClaudeCodeClient(McpClientInfo.Scope.GLOBAL, configPath)
    val types = client.getConfiguredTransportTypes()

    assertEquals(0, types.size)
  }

  @Test
  fun `getConfiguredTransportTypes returns empty set for empty config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"mcpServers": {}}""")

    val client = WindsurfClient(McpClientInfo.Scope.GLOBAL, configPath)
    val types = client.getConfiguredTransportTypes()

    assertEquals(0, types.size)
  }

  @Test
  fun `getTransportTypesDisplayString returns Stdio`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "mcpServers": {
          "test": {
            "command": "java",
            "args": ["MainKt"]
          }
        }
      }
      """.trimIndent()
    )

    val client = CursorClient(McpClientInfo.Scope.GLOBAL, configPath)
    val displayString = client.getTransportTypesDisplayString()

    assertEquals("Stdio", displayString)
  }

  @Test
  fun `getTransportTypesDisplayString returns SSE`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "mcpServers": {
          "test": {
            "url": "http://localhost:8123/sse",
            "type": "sse"
          }
        }
      }
      """.trimIndent()
    )

    val client = WindsurfClient(McpClientInfo.Scope.GLOBAL, configPath)
    val displayString = client.getTransportTypesDisplayString()

    assertEquals("SSE", displayString)
  }

  @Test
  fun `getTransportTypesDisplayString returns null for empty config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"mcpServers": {}}""")

    val client = WindsurfClient(McpClientInfo.Scope.GLOBAL, configPath)
    val displayString = client.getTransportTypesDisplayString()

    assertNull(displayString)
  }

  @Test
  fun `getTransportTypesDisplayString returns null for missing config`() {
    val configPath = tempDir.resolve("missing.json")

    val client = ClaudeCodeClient(McpClientInfo.Scope.GLOBAL, configPath)
    val displayString = client.getTransportTypesDisplayString()

    assertNull(displayString)
  }

  @Test
  fun `getConfiguredTransportTypes detects Stdio in TOML config`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.test]
      command = "java"
      args = ["MainKt"]

      [mcp_servers.test.env]
      IJ_MCP_SERVER_PORT = "8080"
      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.GLOBAL, configPath)
    val types = client.getConfiguredTransportTypes()

    assertEquals(1, types.size)
    assertTrue(types.contains(TransportType.STDIO))
  }

  @Test
  fun `getConfiguredTransportTypes detects HTTP Stream in TOML config`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [mcp_servers.test]
      url = "http://localhost:8123/stream"
      """.trimIndent()
    )

    val client = CodexClient(McpClientInfo.Scope.GLOBAL, configPath)
    val types = client.getConfiguredTransportTypes()

    assertEquals(1, types.size)
    assertTrue(types.contains(TransportType.STREAMABLE_HTTP))
  }
}
