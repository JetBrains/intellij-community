package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ClaudeCodeNetworkConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import java.nio.file.Path

class ClaudeCodeClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.CLAUDE_CODE, scope),
  configPath = configPath
) {

  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val network = isSSEOrStreamConfigured() ?: return null
    return stdio || network
  }

  override suspend fun getSSEConfig(): ServerConfig = ClaudeCodeNetworkConfig(type = "sse", url = sseUrl, headers = buildScopeHeaders())

  override suspend fun getStreamableHttpConfig(): ServerConfig = ClaudeCodeNetworkConfig(type = "http", url = streamableHttpUrl, headers = buildScopeHeaders())
}