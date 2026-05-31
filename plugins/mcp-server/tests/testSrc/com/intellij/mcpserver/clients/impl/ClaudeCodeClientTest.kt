package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.writeText

@TestApplication
class ClaudeCodeClientTest : VscodeForkMcpClientTest() {
  override fun createClient(scope: McpClientInfo.Scope, configPath: Path): McpClient =
    ClaudeCodeClient(scope, configPath)

  override fun getTestOverrideKey(): String = "claudecodetest"

  override fun getMcpServersKey(): String = "mcpServers"

  @Test
  fun `configure writes project scoped config to mcp file`() {
    val configPath = tempDir.resolve(".mcp.json")
    configPath.writeText("""{"mcpServers": {}}""")

    McpClient.overrideProductSpecificServerKeyForTests(getTestOverrideKey())

    val client = createClient(McpClientInfo.Scope.Project(tempDir.toString()), configPath)
    runBlocking(Dispatchers.Default) {
      client.configure(getStreamableHttpConfigOrThrow(client))
    }

    val servers = readServers(client, configPath)
    val config = servers[getTestOverrideKey()]
    requireNotNull(config)
    assertThat(config.url).isEqualTo("http://localhost:7777/stream")
    assertThat(config.type).isEqualTo("http")
  }
}
