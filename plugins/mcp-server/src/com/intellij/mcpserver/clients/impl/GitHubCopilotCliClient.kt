// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.GitHubCopilotNetworkConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import java.nio.file.Path

/**
 * MCP client integration for the `github/copilot-cli` tool.
 *
 * JSON config files use the `"mcpServers"` top-level key. The CLI accepts
 * several locations:
 *  - Global: `~/.copilot/mcp.json` (preferred) or the legacy
 *    `~/.copilot/mcp-config.json`, both overridable via `$COPILOT_HOME`.
 *  - Project: `.github/mcp.json` (preferred) or `mcp.json` at the project
 *    root.
 *
 * Reading is handled by the base [McpClient]; its `@JsonNames("servers",
 * "mcpServers")` accepts either key, so we only choose the *write* key here.
 */
class GitHubCopilotCliClient(
  scope: McpClientInfo.Scope,
  configPath: Path,
) : McpClient(
  mcpClientInfo = McpClientInfo(
    name = McpClientInfo.Name.GITHUB_COPILOT_CLI,
    scope = scope,
  ),
  configPath = configPath,
) {

  override fun isConfigured(): Boolean? {
    val stdio = isStdIOConfigured() ?: return null
    val network = isSSEOrStreamConfigured() ?: return null
    return stdio || network
  }

  override suspend fun getSSEConfig(): ServerConfig =
    GitHubCopilotNetworkConfig(url = sseUrl, type = "sse", headers = buildScopeHeaders())

  override suspend fun getStreamableHttpConfig(): ServerConfig =
    GitHubCopilotNetworkConfig(url = streamableHttpUrl, type = "http", headers = buildScopeHeaders())

  override fun mcpServersKey(): String = "mcpServers"
}
