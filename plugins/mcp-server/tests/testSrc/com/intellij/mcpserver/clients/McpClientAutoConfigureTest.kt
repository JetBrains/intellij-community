package com.intellij.mcpserver.clients

import com.intellij.mcpserver.clients.impl.AirClient
import com.intellij.mcpserver.clients.impl.ClaudeCodeClient
import com.intellij.mcpserver.clients.impl.CodexClient
import com.intellij.mcpserver.clients.impl.CursorClient
import com.intellij.mcpserver.clients.impl.JunieClient
import com.intellij.mcpserver.clients.impl.TestMcpServerConnectionAddressProvider
import com.intellij.mcpserver.clients.impl.TestMcpServerService
import com.intellij.mcpserver.clients.impl.VSCodeClient
import com.intellij.mcpserver.clients.impl.WindsurfClient
import com.intellij.mcpserver.clients.impl.readServers
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.replacedServiceFixture
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Tests for MCP client autoconfiguration fallback chain.
 * 
 * Tests verify that autoConfigure() attempts transports in the correct order:
 * 1. Streamable HTTP (if supported)
 * 2. SSE (if supported)
 * 3. Stdio (always supported)
 * 
 * Also tests that configuration preserves unrelated content in config files.
 */
@TestApplication
class McpClientAutoConfigureTest {
  @TempDir
  lateinit var tempDir: Path

  @Suppress("unused")
  private val connectionAddressProvider = application.replacedServiceFixture(
    McpServerConnectionAddressProvider::class.java
  ) {
    TestMcpServerConnectionAddressProvider(
      streamUrl = "http://localhost:7777/stream",
      sseUrl = "http://localhost:7777/sse"
    )
  }

  @Suppress("unused")
  private val mcpServerService = application.replacedServiceFixture(
    McpServerService::class.java
  ) {
    TestMcpServerService(CoroutineScope(Dispatchers.Default), 7777)
  }

  @AfterEach
  fun resetOverrides() {
    McpClient.overrideProductSpecificServerKeyForTests(null)
    McpClient.overrideWriteLegacyForTests(null)
  }

  @Test
  fun `Cursor autoConfigure with HTTP Stream succeeds`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"mcpServers": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = CursorClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val servers = readServers(client, configPath)
    val config = servers["test"]
    requireNotNull(config)
    assertEquals("http://localhost:7777/stream", config.url!!)
    assertEquals("http", config.type!!)
  }

  @Test
  fun `VSCode autoConfigure with HTTP Stream succeeds`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"servers": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = VSCodeClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val servers = readServers(client, configPath)
    val config = servers["test"]
    requireNotNull(config)
    assertEquals("http://localhost:7777/stream", config.url!!)
    assertEquals("http", config.type!!)
  }

  @Test
  fun `Windsurf autoConfigure with HTTP Stream succeeds`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"mcpServers": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = WindsurfClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val servers = readServers(client, configPath)
    val config = servers["test"]
    requireNotNull(config)
    assertEquals("http://localhost:7777/stream", config.url!!)
    assertEquals("http", config.type!!)
  }

  @Test
  fun `Codex autoConfigure with HTTP Stream succeeds`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText("")

    McpClient.overrideProductSpecificServerKeyForTests("codextest")

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    // CodexClient overrides streamableHttpUrl, so it doesn't need service substitution
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val result = configPath.readText()
    assertTrue(result.contains("[mcp_servers.codextest]"))
    assertTrue(result.contains("url ="))
    assertTrue(result.contains("/stream"))
  }

  @Test
  fun `Junie autoConfigure with HTTP Stream succeeds`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"mcpServers": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = JunieClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val servers = readServers(client, configPath)
    val config = servers["test"]
    requireNotNull(config)
    assertEquals("http://localhost:7777/stream", config.url!!)
    assertEquals("http", config.type!!)
  }

  @Test
  fun `Claude Code autoConfigure with HTTP Stream succeeds`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"mcpServers": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = ClaudeCodeClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val servers = readServers(client, configPath)
    val config = servers["test"]
    requireNotNull(config)
    assertEquals("http://localhost:7777/stream", config.url!!)
    assertEquals("http", config.type!!)
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `Air autoConfigure with HTTP Stream succeeds`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"mcpServers": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = AirClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val servers = readServers(client, configPath)
    val config = servers["test"]
    requireNotNull(config)
    assertEquals("http://localhost:7777/stream", config.url!!)
    assertEquals("http", config.type!!)
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `autoConfigure preserves projects section in JSON config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "projects": {
          "/Users/test/my project": {
            "trust_level": "trusted"
          }
        },
        "mcpServers": {}
      }
      """.trimIndent()
    )

    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = CursorClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.EDT) {
      client.autoConfigure()
    }

    val config = McpClient.json.decodeFromStream<JsonObject>(configPath.inputStream())
    requireNotNull(config["projects"])
    requireNotNull(config["mcpServers"])
  }

  @Test
  fun `autoConfigure preserves unrelated mcpServers entries`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "mcpServers": {
          "my-custom-server": {
            "command": "custom-command",
            "args": ["arg1", "arg2"]
          },
          "another-server": {
            "url": "http://localhost:5000/sse",
            "type": "sse"
          }
        }
      }
      """.trimIndent()
    )


    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = CursorClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val servers = readServers(client, configPath)
    assertTrue(servers.containsKey("my-custom-server"))
    assertTrue(servers.containsKey("another-server"))
    assertTrue(servers.containsKey("test"))
  }

  @Test
  fun `autoConfigure removes legacy jetbrains key when writeLegacy is false`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "servers": {
          "jetbrains": {
            "command": "old-command"
          }
        }
      }
      """.trimIndent()
    )

    McpClient.overrideProductSpecificServerKeyForTests("test")
    McpClient.overrideWriteLegacyForTests(false)

    val client = VSCodeClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val servers = readServers(client, configPath)
    assertFalse(servers.containsKey("jetbrains"))
    assertTrue(servers.containsKey("test"))
  }

  @Test
  fun `Codex autoConfigure preserves projects section in TOML config`() {
    val configPath = tempDir.resolve("config.toml")
    configPath.writeText(
      """
      [projects."/Users/test/project"]
      trust_level = "trusted"
      """.trimIndent()
    )

    McpClient.overrideProductSpecificServerKeyForTests("codextest")

    val client = CodexClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val result = configPath.readText()
    assertTrue(result.contains("""[projects."/Users/test/project"]"""))
    assertTrue(result.contains("trust_level"))
    assertTrue(result.contains("[mcp_servers.codextest]"))
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `autoConfigure with project path includes headers in JSON config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"mcpServers": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = ClaudeCodeClient(McpClientInfo.Scope.Project("/my/project"), configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val config = McpClient.json.decodeFromStream<JsonObject>(configPath.inputStream())
    val serverEntry = config["mcpServers"]?.jsonObject?.get("test")?.jsonObject
    requireNotNull(serverEntry)
    val headers = serverEntry["headers"]?.jsonObject
    requireNotNull(headers)
    assertEquals("/my/project", headers[IJ_MCP_SERVER_PROJECT_PATH]?.jsonPrimitive?.contentOrNull)
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Test
  fun `autoConfigure without project path omits headers from JSON config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"mcpServers": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests("test")

    val client = ClaudeCodeClient(McpClientInfo.Scope.Global, configPath)
    runBlocking(Dispatchers.Default) {
      client.autoConfigure()
    }

    val config = McpClient.json.decodeFromStream<JsonObject>(configPath.inputStream())
    val serverEntry = config["mcpServers"]?.jsonObject?.get("test")?.jsonObject
    requireNotNull(serverEntry)
    assertFalse(serverEntry.containsKey("headers"))
  }
}
