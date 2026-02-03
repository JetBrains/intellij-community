package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import java.nio.file.Path


class ClaudeClient(scope: McpClientInfo.Scope, configPath: Path) : McpClient(
  mcpClientInfo = McpClientInfo(McpClientInfo.Name.CLAUDE_APP, scope),
  configPath = configPath
) {
  override fun isConfigured(): Boolean? = isStdIOConfigured()
}