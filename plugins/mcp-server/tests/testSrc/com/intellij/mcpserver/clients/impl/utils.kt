package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.impl.util.network.McpServerConnectionAddressProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.io.path.inputStream

/**
 * Test implementation of McpServerConnectionAddressProvider that returns fixed URLs.
 * Used for testing autoconfigure behavior with controlled network addresses.
 */
internal class TestMcpServerConnectionAddressProvider(
  private val streamUrl: String,
  private val sseUrl: String
) : McpServerConnectionAddressProvider() {
  override val serverStreamUrl: String get() = streamUrl
  override val serverSseUrl: String get() = sseUrl
}

/**
 * Test implementation of McpServerService that provides a fixed port without requiring server startup.
 * Used in client configuration tests where the actual MCP server doesn't need to be running.
 */
internal class TestMcpServerService(cs: CoroutineScope, override val port: Int) : McpServerService(cs)

/**
 * Reads the mcp servers section from a JSON config file.
 */
internal fun readServers(client: McpClient, configPath: Path): Map<String, ExistingConfig> {
  val config = McpClient.json.decodeFromStream<JsonObject>(configPath.inputStream())
  return config[client.mcpServersKey()]?.jsonObject?.mapValues { (_, value) ->
    McpClient.json.decodeFromJsonElement(ExistingConfig.serializer(), value)
  } ?: emptyMap()
}