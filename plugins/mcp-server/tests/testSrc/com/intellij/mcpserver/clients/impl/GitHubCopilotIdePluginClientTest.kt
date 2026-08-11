package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class GitHubCopilotIdePluginClientTest : VscodeForkMcpClientTest() {
  override fun createClient(scope: McpClientInfo.Scope, configPath: Path): McpClient =
    GitHubCopilotIdePluginClient(scope, configPath)

  override fun getTestOverrideKey(): String = "githubcopilotidetest"

  override fun getMcpServersKey(): String = "servers"

  override fun getStreamableHttpConfigOrThrow(client: McpClient): ServerConfig = runBlocking(Dispatchers.Default) {
    client.getStreamableHttpConfig()!!
  }

  override fun getUnrelatedSectionsTestJson(): String = """
    {
      "customSection": {
        "key": "value"
      },
      "servers": {
        "custom-server": {
          "command": "custom"
        }
      }
    }
  """.trimIndent()

  override fun verifyUnrelatedSectionsPreserved(result: String) {
    assertThat(result).contains("customSection")
  }

  @Test
  fun `mcpServersKey returns servers for IDE plugin variant`() {
    val configPath = tempDir.resolve("config.json")
    val client = GitHubCopilotIdePluginClient(McpClientInfo.Scope.Global, configPath)
    assertThat(client.mcpServersKey()).isEqualTo("servers")
  }
}
