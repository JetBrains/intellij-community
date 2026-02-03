package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.clients.configs.VSCodeConfig
import com.intellij.mcpserver.clients.configs.VSCodeNetworkConfig
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
    val network = isSSEOrStreamConfigured() ?: return null
    return stdio || network
  }

  override fun getSSEConfig(): ServerConfig = VSCodeNetworkConfig(url = sseUrl, type = "sse")

  override fun getStreamableHttpConfig(): ServerConfig? = VSCodeNetworkConfig(url = streamableHttpUrl, type = "http")

  override fun mcpServersKey(): String = "servers"

  @OptIn(ExperimentalSerializationApi::class)
  override fun readMcpServers(): Map<String, ExistingConfig>? {
    return runCatching {
      if (!configPath.exists()) return null
      json.decodeFromStream<VSCodeConfig>(configPath.inputStream()).servers ?: emptyMap()
    }.getOrNull()
  }
}
