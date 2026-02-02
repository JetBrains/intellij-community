package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.CodexStreamableHttpConfig
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.util.io.createParentDirectories
import com.moandjiezana.toml.Toml
import com.moandjiezana.toml.TomlWriter
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

open class CodexClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.CODEX, scope),
  configPath
) {
  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val sse = isSSEConfigured() ?: return null
    return stdio || sse
  }

  override fun getSSEConfig(): ServerConfig = CodexStreamableHttpConfig(url = streamableHttpUrl)

  override fun readMcpServers(): Map<String, ExistingConfig>? {
    if (!configPath.exists()) return null
    return runCatching { parseCodexServers(configPath.readText()) }.getOrNull()
  }

  override fun configure() {
    val existingContent = if (configPath.exists()) configPath.readText() else ""
    val productServerKey = productSpecificServerKey()

    val updatedContent = updateCodexConfig(
      existing = existingContent,
      productServerKey = productServerKey,
      legacyKeys = LEGACY_SERVER_KEYS,
      url = streamableHttpUrl
    )

    configPath.parent?.createParentDirectories()
    configPath.writeText(updatedContent)
  }

  companion object {
    private const val MCP_SERVERS = "mcp_servers"

    private fun parseCodexServers(content: String): Map<String, ExistingConfig> {
      val root = Toml().read(content).toMap()
      val serversAny = root[MCP_SERVERS] as? Map<*, *> ?: return emptyMap()

      val result = LinkedHashMap<String, ExistingConfig>(serversAny.size)
      for ((k, v) in serversAny) {
        val serverName = (k as? String)?.trim().orEmpty()
        if (serverName.isEmpty()) continue

        val table = v as? Map<*, *> ?: continue

        val command = table["command"] as? String
        val type = (table["type"] as? String) ?: (table["transport"] as? String)
        val url = (table["url"] as? String) ?: (table["serverUrl"] as? String)

        val args = (table["args"] as? List<*>)?.filterIsInstance<String>()?.ifEmpty { null }

        val env = (table["env"] as? Map<*, *>)?.entries
          ?.mapNotNull { (ek, ev) ->
            val key = ek as? String ?: return@mapNotNull null
            val value = ev as? String ?: return@mapNotNull null
            key to value
          }
          ?.toMap()
          ?.ifEmpty { null }

        result[serverName] = ExistingConfig(
          command = command,
          args = args,
          env = env,
          url = url,
          type = type,
        )
      }
      return result
    }

    private fun updateCodexConfig(
      existing: String,
      productServerKey: String,
      legacyKeys: Set<String>,
      url: String
    ): String {
      val root = Toml().read(existing).toMap()

      val existingServers = (root[MCP_SERVERS] as? Map<*, *>) ?: emptyMap<Any, Any>()

      val serversWithoutLegacy = existingServers.filterKeys { key ->
        key !in legacyKeys || key == productServerKey
      }

      val existingProductTable = (serversWithoutLegacy[productServerKey] as? Map<*, *>) ?: emptyMap<Any, Any>()
      val updatedProductTable = existingProductTable + ("url" to url)
      
      val updatedServers = serversWithoutLegacy + (productServerKey to updatedProductTable)

      val updatedRoot = root + (MCP_SERVERS to updatedServers)

      val writer = TomlWriter()
      val rendered = writer.write(updatedRoot)

      return if (rendered.endsWith("\n")) rendered else "$rendered\n"
    }
  }
}
