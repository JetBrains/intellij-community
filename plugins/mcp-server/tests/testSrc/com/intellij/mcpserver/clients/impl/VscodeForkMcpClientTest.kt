package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import com.intellij.testFramework.junit5.fixture.replacedServiceFixture
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Abstract base class for VSCode fork MCP client tests (VSCode, Cursor, Windsurf).
 * Extracts common test setup and test methods to eliminate duplication across client implementations.
 *
 * Subclasses must implement:
 * - [createClient]: Factory method to instantiate the specific client type
 * - [getTestOverrideKey]: Returns the test key for server configuration ("vscodetest", "cursortest", etc.)
 * - [getMcpServersKey]: Returns the config section name ("servers" for VSCode, "mcpServers" for others)
 *
 * Subclasses may override:
 * - [getStreamableHttpConfigOrThrow]: Handles nullable vs non-nullable config retrieval
 * - [getUnrelatedSectionsTestJson]: Provides JSON for unrelated sections test
 * - [verifyUnrelatedSectionsPreserved]: Validates preserved sections in autoconfigure test
 */
abstract class VscodeForkMcpClientTest {
  @TempDir
  lateinit var tempDir: Path

  @Suppress("unused") // Property initializes test fixture
  private val connectionAddressProvider = application.replacedServiceFixture(
    McpServerConnectionAddressProvider::class.java
  ) {
    TestMcpServerConnectionAddressProvider(
      streamUrl = "http://localhost:7777/stream",
      sseUrl = "http://localhost:7777/sse"
    )
  }

  @Suppress("unused") // Property initializes test fixture
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

  /**
   * Factory method to create the specific client implementation being tested.
   */
  protected abstract fun createClient(scope: McpClientInfo.Scope, configPath: Path): McpClient

  /**
   * Returns the test override key used for server configuration.
   * Examples: "vscodetest", "cursortest", "windsurftest"
   */
  protected abstract fun getTestOverrideKey(): String

  /**
   * Returns the JSON key used for the MCP servers section in config files.
   * Returns "servers" for VSCode, "mcpServers" for Cursor/Windsurf.
   */
  protected abstract fun getMcpServersKey(): String

  /**
   * Gets the streamable HTTP config, throwing if null.
   * Default implementation throws an error if null. VSCode overrides to use `!!`.
   */
  protected open fun getStreamableHttpConfigOrThrow(client: McpClient): ServerConfig {
    return runBlocking(Dispatchers.Default) { client.getStreamableHttpConfig() } ?: error("Streamable HTTP config is null")
  }

  /**
   * Provides JSON content for the unrelated sections preservation test.
   * Default uses "projects" section, VSCode overrides to use "customSection".
   */
  protected open fun getUnrelatedSectionsTestJson(): String = """
    {
      "projects": {
        "/Users/test/project": {
          "trust_level": "trusted"
        }
      },
      "${getMcpServersKey()}": {
        "custom-server": {
          "command": "custom"
        }
      }
    }
  """.trimIndent()

  /**
   * Verifies that unrelated sections are preserved after autoconfigure.
   * Default checks for "projects" section, VSCode overrides to check "customSection".
   */
  protected open fun verifyUnrelatedSectionsPreserved(result: String) {
    assertThat(result).contains("projects")
    assertThat(result).contains("/Users/test/project")
  }

  @Test
  fun `isConfigured returns true for HTTP Stream url`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "${getMcpServersKey()}": {
          "test": {
            "url": "http://localhost:8123/stream"
          }
        }
      }
      """.trimIndent()
    )

    val client = createClient(McpClientInfo.Scope.GLOBAL, configPath)
    assertThat(client.isConfigured()).isTrue()
  }

  @Test
  fun `configure with HTTP Stream config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"${getMcpServersKey()}": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests(getTestOverrideKey())

    val client = createClient(McpClientInfo.Scope.GLOBAL, configPath)
    runBlocking(Dispatchers.Default) {
      client.configure(getStreamableHttpConfigOrThrow(client))
    }

    val servers = readServers(client, configPath)
    val config = servers[getTestOverrideKey()]
    requireNotNull(config)
    assertThat(config.url).isEqualTo("http://localhost:7777/stream")
    assertThat(config.type).isEqualTo("http")
  }

  @Test
  fun `configure with SSE config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"${getMcpServersKey()}": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests(getTestOverrideKey())

    val client = createClient(McpClientInfo.Scope.GLOBAL, configPath)
    val sseConfig = runBlocking(Dispatchers.Default) { client.getSSEConfig() } ?: error("SSE config is null")
    runBlocking(Dispatchers.Default) { client.configure(sseConfig) }

    val servers = readServers(client, configPath)
    val config = servers[getTestOverrideKey()]
    requireNotNull(config)
    assertThat(config.url).isEqualTo("http://localhost:7777/sse")
    assertThat(config.type).isEqualTo("sse")
  }

  @Test
  fun `configure with Stdio config`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText("""{"${getMcpServersKey()}": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests(getTestOverrideKey())

    val client = createClient(McpClientInfo.Scope.GLOBAL, configPath)
    runBlocking(Dispatchers.Default) { client.configure(client.getStdioConfig()) }

    val servers = readServers(client, configPath)
    val config = servers[getTestOverrideKey()]
    requireNotNull(config)
    requireNotNull(config.command)
    requireNotNull(config.args)
    assertThat(config.args).anyMatch { it.contains("McpStdioRunnerKt") }
  }

  @Test
  fun `configure drops legacy section when legacy disabled`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(
      """
      {
        "${getMcpServersKey()}": {
          "jetbrains": {
            "url": "http://localhost:9999/stream"
          }
        }
      }
      """.trimIndent()
    )

    McpClient.overrideProductSpecificServerKeyForTests(getTestOverrideKey())
    McpClient.overrideWriteLegacyForTests(false)

    val client = createClient(McpClientInfo.Scope.GLOBAL, configPath)
    runBlocking(Dispatchers.Default) { client.configure(getStreamableHttpConfigOrThrow(client)) }

    val result = configPath.readText()
    assertThat(result).contains(getTestOverrideKey())
    assertThat(result).doesNotContain("jetbrains")
  }

  @Test
  fun `autoConfigure preserves unrelated sections`() {
    val configPath = tempDir.resolve("config.json")
    configPath.writeText(getUnrelatedSectionsTestJson())

    McpClient.overrideProductSpecificServerKeyForTests(getTestOverrideKey())

    val client = createClient(McpClientInfo.Scope.GLOBAL, configPath)
    runBlocking(Dispatchers.Default) { client.autoConfigure() }

    val result = configPath.readText()
    verifyUnrelatedSectionsPreserved(result)
    assertThat(result).contains("custom-server")
    assertThat(result).contains(getTestOverrideKey())
  }
}
