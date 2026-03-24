package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.clients.configs.VSCodeConfig
import com.intellij.mcpserver.clients.configs.VSCodeNetworkConfig
import com.intellij.openapi.components.service
import com.intellij.util.application
import com.intellij.util.text.SemVer
import kotlinx.coroutines.Deferred
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
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

  private val vsCodeVersion: Deferred<SemVer?> = application.service<McpServiceScope>().scope.getSemVerOfVscodeFork("code")

  override suspend fun getSSEConfig(): ServerConfig = VSCodeNetworkConfig(url = sseUrl, type = "sse")

  override suspend fun getStreamableHttpConfig(): ServerConfig? {
    // VScode supports streamable HTTP since 1.100.0 (https://github.com/microsoft/vscode/releases/tag/1.100.0)
    return if (vsCodeVersion.await()?.isGreaterOrEqualThan(1, 100, 0) == false  ) {
      null
    }
    else {
      VSCodeNetworkConfig(url = streamableHttpUrl, type = "http")
    }
  }

  override fun mcpServersKey(): String = "servers"

  @OptIn(ExperimentalSerializationApi::class)
  override fun readMcpServers(): Map<String, ExistingConfig>? {
    return runCatching {
      if (!configPath.exists()) return null
      json.decodeFromStream<VSCodeConfig>(configPath.inputStream()).servers ?: emptyMap()
    }.getOrNull()
  }
}
