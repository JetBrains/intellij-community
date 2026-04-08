package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.JunieNetworkConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import java.nio.file.Path

class JunieClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.JUNIE, scope),
  configPath = configPath
) {
  override fun isConfigured(): Boolean = isSSEOrStreamConfigured() == true || isStdIOConfigured() == true

  override suspend fun getStreamableHttpConfig(): ServerConfig = JunieNetworkConfig(url = streamableHttpUrl, type = "http")
}