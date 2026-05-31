package com.intellij.mcpserver.clients.impl

import com.electronwill.nightconfig.core.CommentedConfig
import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.electronwill.nightconfig.core.io.ParsingMode
import com.electronwill.nightconfig.toml.TomlFormat
import com.electronwill.nightconfig.toml.TomlWriter
import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.CodexStreamableHttpConfig
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.clients.configs.STDIOServerConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.util.io.createParentDirectories
import java.io.StringWriter
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
    val network = isSSEOrStreamConfigured() ?: return null
    return stdio || network
  }

  override suspend fun getStreamableHttpConfig(): ServerConfig = CodexStreamableHttpConfig(url = streamableHttpUrl, headers = buildScopeHeaders())

  override fun readMcpServers(): Map<String, ExistingConfig>? {
    if (!configPath.exists()) return null
    return runCatching { parseCodexServers(configPath.readText()) }.getOrNull()
  }

  override suspend fun configure(config: ServerConfig) {
    val existingContent = if (configPath.exists()) configPath.readText() else ""
    val productServerKey = productSpecificServerKey()

    val updatedContent = updateCodexConfig(
      existing = existingContent,
      productServerKey = productServerKey,
      legacyKeys = LEGACY_SERVER_KEYS,
      config = config
    )

    configPath.createParentDirectories()
    configPath.writeText(updatedContent)
  }

  companion object {
    private const val MCP_SERVERS = "mcp_servers"

    private fun parseCodexServers(content: String): Map<String, ExistingConfig> {
      val servers = parseTomlConfig(content).readConfig(MCP_SERVERS) ?: return emptyMap()

      val result = LinkedHashMap<String, ExistingConfig>(servers.size())
      for (entry in servers.entrySet()) {
        val serverName = entry.key.trim()
        if (serverName.isEmpty()) continue

        val table = entry.getRawValue<Any>() as? UnmodifiableConfig ?: continue

        val command = table.readString("command")
        val url = table.readString("url") ?: table.readString("serverUrl")

        result[serverName] = ExistingConfig(
          command = command,
          args = table.readStringList("args"),
          env = table.readStringMap("env"),
          url = url,
          type = if (command != null) "stdio" else "http",
        )
      }
      return result
    }

    private fun updateCodexConfig(
      existing: String,
      productServerKey: String,
      legacyKeys: Set<String>,
      config: ServerConfig,
    ): String {
      val root = parseTomlConfig(existing)
      val servers = root.readMutableConfig(MCP_SERVERS) ?: newTomlConfig().also {
        root.set<Any?>(listOf(MCP_SERVERS), it)
      }

      for (legacyKey in legacyKeys) {
        if (legacyKey != productServerKey) {
          servers.remove<Any?>(listOf(legacyKey))
          servers.removeComment(listOf(legacyKey))
        }
      }

      servers.set<Any?>(listOf(productServerKey), config.toTomlConfig())

      val rendered = renderTomlConfig(root)

      return if (rendered.endsWith("\n")) rendered else "$rendered\n"
    }

    private fun parseTomlConfig(content: String): CommentedConfig {
      val config = newTomlConfig()
      TomlFormat.instance().createParser().parse(content, config, ParsingMode.REPLACE)
      return config
    }

    private fun newTomlConfig(): CommentedConfig = TomlFormat.newConfig { LinkedHashMap<String, Any>() }

    private fun renderTomlConfig(config: UnmodifiableConfig): String {
      val writer = TomlWriter().apply {
        setIndent("  ")
        setNewline("\n")
      }
      val output = StringWriter()
      writer.write(config, output)
      return output.toString()
    }

    private fun ServerConfig.toTomlConfig(): CommentedConfig {
      val table = newTomlConfig()
      when (this) {
        is CodexStreamableHttpConfig -> table.set<Any?>(listOf("url"), url)
          .also { headers?.takeIf { it.isNotEmpty() }?.let { table.set(listOf("headers"), it.toTomlConfig()) } }
        is STDIOServerConfig -> {
          command?.let { table.set<Any?>(listOf("command"), it) }
          args?.let { table.set<Any?>(listOf("args"), it) }
          env?.takeIf { it.isNotEmpty() }?.let { table.set(listOf("env"), it.toTomlConfig()) }
        }
        else -> throw IllegalArgumentException("Unexpected config type: ${this::class.java}")
      }
      return table
    }

    private fun Map<String, String>.toTomlConfig(): CommentedConfig {
      val table = newTomlConfig()
      forEach { (key, value) -> table.set<Any?>(listOf(key), value) }
      return table
    }

    private fun UnmodifiableConfig.readConfig(key: String): UnmodifiableConfig? =
      getRaw<Any>(listOf(key)) as? UnmodifiableConfig

    private fun CommentedConfig.readMutableConfig(key: String): CommentedConfig? =
      getRaw<Any>(listOf(key)) as? CommentedConfig

    private fun UnmodifiableConfig.readString(key: String): String? =
      getRaw<Any>(listOf(key)) as? String

    private fun UnmodifiableConfig.readStringList(key: String): List<String>? =
      (getRaw<Any>(listOf(key)) as? List<*>)?.filterIsInstance<String>()?.ifEmpty { null }

    private fun UnmodifiableConfig.readStringMap(key: String): Map<String, String>? {
      val config = readConfig(key) ?: return null
      val result = LinkedHashMap<String, String>(config.size())
      for (entry in config.entrySet()) {
        val value = entry.getRawValue<Any>() as? String ?: continue
        result[entry.key] = value
      }
      return result.ifEmpty { null }
    }
  }
}
