package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.CodexStreamableHttpConfig
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.util.io.createParentDirectories
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

    var updatedContent = existingContent
    LEGACY_SERVER_KEYS.forEach { legacyKey ->
      if (legacyKey != productServerKey) {
        updatedContent = removeCodexSection(updatedContent, legacyKey)
      }
    }

    updatedContent = updateCodexConfig(updatedContent, productServerKey, streamableHttpUrl)

    configPath.parent?.createParentDirectories()
    configPath.writeText(updatedContent)
  }

  companion object {
    private val SERVER_SECTION_REGEX = Regex("(?mis)^\\s*\\[mcp_servers\\.([^]]+)]\\s*(.*?)(?=^\\s*\\[|\\z)")

    private fun parseCodexServers(content: String): Map<String, ExistingConfig> {
      val servers = mutableMapOf<String, ExistingConfig>()
      SERVER_SECTION_REGEX.findAll(content).forEach { matchResult ->
        val serverName = matchResult.groupValues[1].trim()
        val body = matchResult.groupValues[2]
        val command = extractTomlString(body, "command")
        val type = extractTomlString(body, "type") ?: extractTomlString(body, "transport")
        val url = extractTomlString(body, "url") ?: extractTomlString(body, "serverUrl")
        val args = extractTomlStringArray(body, "args")
        val env = extractTomlInlineTable(body, "env")
        servers[serverName] = ExistingConfig(
          command = command,
          args = args,
          env = env,
          url = url,
          type = type,
        )
      }
      return servers
    }

    private fun extractTomlString(body: String, key: String): String? {
      val regex = Regex("(?mis)^\\s*${Regex.escape(key)}\\s*=\\s*\"((?:\\\\.|[^\"])*)\"")
      val match = regex.find(body) ?: return null
      return unescapeTomlString(match.groupValues[1])
    }

    private fun extractTomlStringArray(body: String, key: String): List<String>? {
      val regex = Regex("(?mis)^\\s*${Regex.escape(key)}\\s*=\\s*\\[(.*?)]")
      val match = regex.find(body) ?: return null
      val inner = match.groupValues[1]
      val valueMatches = Regex("\"((?:\\\\.|[^\"])*)\"").findAll(inner)
      val values = valueMatches.map { unescapeTomlString(it.groupValues[1]) }.toList()
      return values.ifEmpty { null }
    }

    private fun extractTomlInlineTable(body: String, key: String): Map<String, String>? {
      val regex = Regex("(?mis)^\\s*${Regex.escape(key)}\\s*=\\s*\\{(.*?)}")
      val match = regex.find(body) ?: return null
      val inner = match.groupValues[1]
      val pairs = Regex("([A-Za-z0-9_.\\-]+)\\s*=\\s*\"((?:\\\\.|[^\"])*)\"").findAll(inner)
      val map = pairs.associate { it.groupValues[1] to unescapeTomlString(it.groupValues[2]) }
      return map.ifEmpty { null }
    }

    private fun unescapeTomlString(value: String): String {
      val result = StringBuilder()
      var index = 0
      while (index < value.length) {
        val ch = value[index]
        if (ch == '\\' && index + 1 < value.length) {
          val next = value[index + 1]
          when (next) {
            '\\' -> result.append('\\')
            '"' -> result.append('"')
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            'b' -> result.append('\b')
            else -> result.append(next)
          }
          index += 2
        }
        else {
          result.append(ch)
          index++
        }
      }
      return result.toString()
    }

    private fun escapeTomlString(value: String): String {
      val result = StringBuilder()
      value.forEach { ch ->
        when (ch) {
          '\\' -> result.append("\\\\")
          '"' -> result.append("\\\"")
          '\n' -> result.append("\\n")
          '\r' -> result.append("\\r")
          '\t' -> result.append("\\t")
          '\b' -> result.append("\\b")
          else -> result.append(ch)
        }
      }
      return result.toString()
    }

    private fun removeCodexSection(existing: String, serverKey: String): String {
      val sectionRegex = codexSectionRegex(serverKey)
      return sectionRegex.replace(existing, "")
    }

    private fun updateCodexConfig(existing: String, serverKey: String, url: String): String {
      val sectionRegex = codexSectionRegex(serverKey)
      val newSection = buildCodexSection(serverKey, url)
      if (sectionRegex.containsMatchIn(existing)) {
        return sectionRegex.replace(existing, newSection)
      }

      if (existing.isBlank()) {
        return newSection
      }

      val builder = StringBuilder(existing)
      val endsWithSingleNewline = existing.endsWith("\n")
      val endsWithDoubleNewline = existing.endsWith("\n\n") || existing.endsWith("\r\n\r\n")
      if (!endsWithSingleNewline) {
        builder.append('\n')
      }
      if (!endsWithDoubleNewline) {
        builder.append('\n')
      }
      builder.append(newSection)
      return builder.toString()
    }

    private fun codexSectionRegex(serverKey: String): Regex {
      val escapedHeader = Regex.escape("mcp_servers.$serverKey")
      return Regex("(?mis)^\\s*\\[$escapedHeader]\\s*(.*?)(?=^\\s*\\[|\\z)")
    }

    private fun buildCodexSection(serverKey: String, url: String): String {
      return "[mcp_servers.$serverKey]\nurl = \"${escapeTomlString(url)}\"\n\n"
    }
  }
}
