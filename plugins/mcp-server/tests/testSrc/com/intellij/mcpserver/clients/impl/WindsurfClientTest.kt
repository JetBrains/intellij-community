package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.testFramework.junit5.TestApplication
import java.nio.file.Path

@TestApplication
class WindsurfClientTest : VscodeForkMcpClientTest() {
  override fun createClient(scope: McpClientInfo.Scope, configPath: Path): McpClient =
    WindsurfClient(scope, configPath)

  override fun getTestOverrideKey(): String = "windsurftest"

  override fun getMcpServersKey(): String = "mcpServers"
}
