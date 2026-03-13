package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.testFramework.junit5.TestApplication
import java.nio.file.Path

@TestApplication
class CursorClientTest : VscodeForkMcpClientTest() {
  override fun createClient(scope: McpClientInfo.Scope, configPath: Path): McpClient =
    CursorClient(scope, configPath)

  override fun getTestOverrideKey(): String = "cursortest"

  override fun getMcpServersKey(): String = "mcpServers"
}
