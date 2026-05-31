package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.AirNetworkConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import java.nio.file.Path

class AirClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.AIR, scope),
  configPath = configPath
) {
  override fun isConfigured(): Boolean = isSSEOrStreamConfigured() == true || isStdIOConfigured() == true

  override suspend fun getStreamableHttpConfig(): ServerConfig = AirNetworkConfig(url = streamableHttpUrl, type = "http", headers = buildScopeHeaders())
}