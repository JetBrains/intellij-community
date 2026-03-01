package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.clients.configs.VSCodeConfig
import com.intellij.mcpserver.clients.configs.VSCodeSSEConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream


open class VSCodeClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.VS_CODE, scope),
  configPath = configPath
) {
  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val sse = isSSEConfigured() ?: return null
    return stdio || sse
  }

  override fun getSSEConfig(): ServerConfig = VSCodeSSEConfig(url = sseUrl, type = "sse")

  @OptIn(ExperimentalSerializationApi::class)
  override fun readMcpServers(): Map<String, ExistingConfig>? {
    return runCatching {
      if (!configPath.exists()) return null
      json.decodeFromStream<VSCodeConfig>(configPath.inputStream()).servers ?: emptyMap()
    }.getOrNull()
  }

  /** VSCode uses a different root structure: `{ "servers": { ... } }` */
  override fun buildUpdatedConfig(existingConfig: JsonObject, serverEntry: ServerConfig): JsonObject {
    val existingServers = existingConfig["servers"]?.jsonObject ?: buildJsonObject {}
    val targetKey = jetBrainsServerKey()

    return buildJsonObject {
      put("servers", buildJsonObject {
        existingServers.forEach { (key, value) ->
          if (key != targetKey && key !in LEGACY_SERVER_KEYS) put(key, value)
        }
        put(targetKey, json.encodeToJsonElement(serverEntry))
        if (writeLegacy() && LEGACY_KEY != targetKey) {
          put(LEGACY_KEY, json.encodeToJsonElement(serverEntry))
        }
      })
    }
  }
}
