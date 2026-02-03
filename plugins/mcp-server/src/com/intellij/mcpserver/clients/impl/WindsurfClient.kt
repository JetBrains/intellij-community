package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.clients.configs.WindsurfNetworkConfig
import java.nio.file.Path


class WindsurfClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.WINDSURF, scope),
  configPath = configPath
) {
  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val network = isSSEOrStreamConfigured() ?: return null
    return stdio || network
  }

  override fun getSSEConfig(): ServerConfig = WindsurfNetworkConfig(serverUrl = sseUrl, type = "sse")

  override fun getStreamableHttpConfig(): ServerConfig = WindsurfNetworkConfig(serverUrl = streamableHttpUrl, type = "http")
}