package com.intellij.mcpserver.clients.impl

import com.intellij.mcpserver.clients.McpClient
import com.intellij.mcpserver.clients.McpClientInfo
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

@TestApplication
class VSCodeClientTest : VscodeForkMcpClientTest() {
  override fun createClient(scope: McpClientInfo.Scope, configPath: Path): McpClient =
    VSCodeClient(scope, configPath)

  override fun getTestOverrideKey(): String = "vscodetest"

  override fun getMcpServersKey(): String = "servers"

  override fun getStreamableHttpConfigOrThrow(client: McpClient): ServerConfig = runBlocking {
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
    assertTrue(result.contains("customSection"))
  }

  @Test
  fun `mcpServersKey returns servers for VSCode`() {
    val configPath = tempDir.resolve("config.json")
    val client = VSCodeClient(McpClientInfo.Scope.GLOBAL, configPath)
    assertEquals("servers", client.mcpServersKey())
  }
}
