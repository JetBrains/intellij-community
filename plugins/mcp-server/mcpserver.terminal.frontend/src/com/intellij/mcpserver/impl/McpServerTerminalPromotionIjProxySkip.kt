package com.intellij.mcpserver.impl

import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.impl.ClaudeCodeClient
import com.intellij.mcpserver.clients.impl.CodexClient
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.nio.file.Paths

private val IJ_PROXY_SERVER_NAMES: Set<String> = setOf("ij-proxy", "ijproxy")

internal fun shouldSkipPromotionDueToProjectIjProxy(project: Project): Boolean {
  val basePath = project.basePath ?: return false
  return projectBaseDirHasIjProxyMcpServer(Paths.get(basePath))
}

@VisibleForTesting
internal fun projectBaseDirHasIjProxyMcpServer(projectBaseDir: Path): Boolean {
  val claudeClient = ClaudeCodeClient(McpClientInfo.Scope.Global, projectBaseDir.resolve(".mcp.json"))
  if (claudeClient.hasAnyMcpServerNamed(IJ_PROXY_SERVER_NAMES)) return true

  val codexClient = CodexClient(McpClientInfo.Scope.Global, projectBaseDir.resolve(".codex").resolve("config.toml"))
  return codexClient.hasAnyMcpServerNamed(IJ_PROXY_SERVER_NAMES)
}