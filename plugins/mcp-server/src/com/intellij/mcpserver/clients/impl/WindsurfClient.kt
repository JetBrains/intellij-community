package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.clients.configs.WindsurfSSEConfig
import java.nio.file.Path


class WindsurfClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.WINDSURF, scope),
  configPath = configPath
) {
  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val sse = isSSEConfigured() ?: return null
    return stdio || sse
  }

  override fun getSSEConfig(): ServerConfig = WindsurfSSEConfig(serverUrl = sseUrl)
}