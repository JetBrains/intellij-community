package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.CursorNetworkConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import java.nio.file.Path


class CursorClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.CURSOR, scope),
  configPath = configPath
) {

  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val network = isSSEOrStreamConfigured() ?: return null
    return stdio || network
  }

  override fun getSSEConfig(): ServerConfig = CursorNetworkConfig(url = sseUrl, type = "sse")

  override fun getStreamableHttpConfig(): ServerConfig = CursorNetworkConfig(url = streamableHttpUrl, type = "http")
}