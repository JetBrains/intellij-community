package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.CursorNetworkConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.util.application
import com.intellij.util.text.SemVer
import kotlinx.coroutines.Deferred
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

  private val cursorVersion: Deferred<SemVer?> = application.service<McpServiceScope>().scope.getSemVerOfVscodeFork("cursor")

  override suspend fun getSSEConfig(): ServerConfig = CursorNetworkConfig(url = sseUrl, type = "sse")

  override suspend fun getStreamableHttpConfig(): ServerConfig? {
    //https://forum.cursor.com/t/please-implement-streamable-http-on-cursor-mcp/82984/7
    return if (cursorVersion.await()?.isGreaterOrEqualThan(1, 0, 0) == false) {
      null
    } else {
      CursorNetworkConfig(url = streamableHttpUrl, type = "http")
    }
  }
}