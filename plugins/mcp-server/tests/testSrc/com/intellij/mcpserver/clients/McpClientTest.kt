package com.intellij.mcpserver.clients

import com.intellij.mcpserver.clients.configs.ExistingConfig
import com.intellij.mcpserver.clients.configs.STDIOServerConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.clients.configs.VSCodeNetworkConfig
import com.intellij.mcpserver.clients.impl.VSCodeClient
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths

@TestApplication
class McpClientTest {

  @AfterEach
  fun resetFlagsAndKeys() {
    McpClient.overrideProductSpecificServerKeyForTests(null)
    McpClient.overrideWriteLegacyForTests(null)
  }

  @Test
  fun `product specific key sanitizes identifiers`() {
    val key = McpClient.computeProductSpecificServerKey(
      productName = "IntelliJ IDEA",
      editionName = "Community Edition",
    )
    assertEquals("intellijideacommunity", key)
  }

  @Test
  fun `product specific key keeps edition word`() {
    val key = McpClient.computeProductSpecificServerKey(
      productName = "PyCharm",
      editionName = "Professional Edition",
    )
    assertEquals("pycharmprofessional", key)
  }

  @Test
  fun `product specific key allows bare idea name`() {
    val key = McpClient.computeProductSpecificServerKey(
      productName = "IDEA",
      editionName = null,
    )
    assertEquals("idea", key)
  }

  @Test
  fun `product specific key falls back to default when info missing`() {
    val key = McpClient.computeProductSpecificServerKey(
      productName = "",
      editionName = null,
    )
    assertEquals("jetbrains", key)
  }

  @Test
  fun `product specific key appends dev suffix when running from sources`() {
    val key = McpClient.computeProductSpecificServerKey(
      productName = "Rider",
      editionName = "Ultimate",
      runningFromSources = true,
    )
    assertEquals("riderultimatedev", key)
  }

  @Test
  fun `general client configuration replaces legacy key when legacy flag OFF`() {
    McpClient.overrideProductSpecificServerKeyForTests("intellijcommunity")
    McpClient.overrideWriteLegacyForTests(false)

    val client = TestableMcpClient()
    val serverEntry = STDIOServerConfig(command = "java", args = listOf("-jar"), env = mapOf("TEST" to "1"))

    val existingConfig = buildJsonObject {
      put("mcpServers", buildJsonObject {
        put("jetbrains", buildJsonObject { put("url", JsonPrimitive("legacy")) })
        put("other", buildJsonObject { put("url", JsonPrimitive("keep")) })
      })
      put("unrelated", JsonPrimitive("value"))
    }

    val updated = client.buildUpdated(existingConfig, serverEntry)
    val servers = updated["mcpServers"]!!.jsonObject

    assertFalse("jetbrains" in servers)
    assertTrue("other" in servers)
    assertTrue("intellijcommunity" in servers)
    assertEquals("value", updated["unrelated"]!!.jsonPrimitive.content)
  }

  @Test
  fun `vscode client configuration replaces legacy key when legacy flag OFF`() {
    McpClient.overrideProductSpecificServerKeyForTests("pycharm")
    McpClient.overrideWriteLegacyForTests(false)

    val client = TestableVSCodeClient()
    val serverEntry = VSCodeNetworkConfig(url = "http://localhost:1234/sse", type = "sse")

    val existingConfig = buildJsonObject {
      put("servers", buildJsonObject {
        put("jetbrains", buildJsonObject { put("url", JsonPrimitive("legacy")) })
        put("extra", buildJsonObject { put("url", JsonPrimitive("keep")) })
      })
    }

    val updated = client.buildUpdated(existingConfig, serverEntry)
    val servers = updated["servers"]!!.jsonObject

    assertFalse("jetbrains" in servers)
    assertTrue("extra" in servers)
    assertTrue("pycharm" in servers)
  }

  @Test
  fun `general client writes both product-specific and legacy when legacy flag ON`() {
    McpClient.overrideProductSpecificServerKeyForTests("intellijcommunity")
    McpClient.overrideWriteLegacyForTests(true)

    val client = TestableMcpClient()
    val serverEntry = STDIOServerConfig(command = "java", args = listOf("-jar"), env = mapOf("TEST" to "1"))

    val existingConfig = buildJsonObject {
      put("mcpServers", buildJsonObject {
        put("legacyToBeReplaced", buildJsonObject { put("url", JsonPrimitive("x")) })
      })
    }

    val updated = client.buildUpdated(existingConfig, serverEntry)
    val servers = updated["mcpServers"]!!.jsonObject

    assertTrue("intellijcommunity" in servers)
    assertTrue("jetbrains" in servers)
    assertEquals("java", servers["intellijcommunity"]!!.jsonObject["command"]!!.jsonPrimitive.content)
    assertEquals("java", servers["jetbrains"]!!.jsonObject["command"]!!.jsonPrimitive.content)
    // unrelated keys preserved by design
    assertTrue("legacyToBeReplaced" in servers)
  }

  @Test
  fun `vscode client writes both product-specific and legacy when legacy flag ON`() {
    McpClient.overrideProductSpecificServerKeyForTests("pycharm")
    McpClient.overrideWriteLegacyForTests(true)

    val client = TestableVSCodeClient()
    val serverEntry = VSCodeNetworkConfig(url = "http://localhost:7777/sse", type = "sse")

    val existingConfig = buildJsonObject {
      put("servers", buildJsonObject {
        put("something", buildJsonObject { put("url", JsonPrimitive("keep")) })
      })
    }

    val updated = client.buildUpdated(existingConfig, serverEntry)
    val servers = updated["servers"]!!.jsonObject

    assertTrue("pycharm" in servers)
    assertTrue("jetbrains" in servers)
    assertEquals("sse", servers["pycharm"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    assertEquals("sse", servers["jetbrains"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    assertEquals("http://localhost:7777/sse", servers["pycharm"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    assertEquals("http://localhost:7777/sse", servers["jetbrains"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    assertTrue("something" in servers)
  }

  @Test
  fun `writing both does not duplicate when product-specific equals legacy`() {
    McpClient.overrideProductSpecificServerKeyForTests("jetbrains")
    McpClient.overrideWriteLegacyForTests(true)

    val client = TestableMcpClient()
    val serverEntry = STDIOServerConfig(command = "java", args = listOf("-jar"), env = emptyMap())

    val existingConfig = buildJsonObject {
      put("mcpServers", buildJsonObject {
        put("other", buildJsonObject { put("url", JsonPrimitive("keep")) })
      })
    }

    val updated = client.buildUpdated(existingConfig, serverEntry)
    val servers = updated["mcpServers"]!!.jsonObject

    assertTrue("jetbrains" in servers)
    assertTrue("other" in servers)
    assertTrue(2 == servers.size)
  }

  @Test
  fun `promotion fallback accepts localhost with similar port`() {
    val client = PromotionAwareMcpClient(
      linkedMapOf("fallback" to ExistingConfig(url = "http://localhost:64347/stream", type = "http"))
    )

    assertTrue(client.hasPromotionCandidateConfig(expectedPort = 64342))
    assertFalse(client.isPortCorrect(expectedPort = 64342))
  }

  @Test
  fun `promotion detects product specific server by name even when port is far off`() {
    McpClient.overrideProductSpecificServerKeyForTests("idea")
    val client = PromotionAwareMcpClient(
      linkedMapOf("idea" to ExistingConfig(url = "http://localhost:71342/stream", type = "http"))
    )

    assertTrue(client.hasPromotionCandidateConfig(expectedPort = 64342))
    assertFalse(client.isPortCorrect(expectedPort = 64342))
  }

  @Test
  fun `promotion fallback accepts localhost within five thousand port range`() {
    val client = PromotionAwareMcpClient(
      linkedMapOf("fallback" to ExistingConfig(url = "http://localhost:68342/stream", type = "http"))
    )

    assertTrue(client.hasPromotionCandidateConfig(expectedPort = 64342))
    assertFalse(client.isPortCorrect(expectedPort = 64342))
  }

  @Test
  fun `promotion fallback accepts 127 0 0 1 with similar port`() {
    val client = PromotionAwareMcpClient(
      linkedMapOf("fallback" to ExistingConfig(url = "http://127.0.0.1:64347/stream", type = "http"))
    )

    assertTrue(client.hasPromotionCandidateConfig(expectedPort = 64342))
    assertFalse(client.isPortCorrect(expectedPort = 64342))
  }

  @Test
  fun `promotion fallback ignores ipv6 loopback`() {
    val client = PromotionAwareMcpClient(
      linkedMapOf("fallback" to ExistingConfig(url = "http://[::1]:64347/stream", type = "http"))
    )

    assertFalse(client.hasPromotionCandidateConfig(expectedPort = 64342))
    assertTrue(client.isPortCorrect(expectedPort = 64342))
  }

  @Test
  fun `promotion fallback ignores non loopback host`() {
    val client = PromotionAwareMcpClient(
      linkedMapOf("fallback" to ExistingConfig(url = "http://192.168.1.20:64347/stream", type = "http"))
    )

    assertFalse(client.hasPromotionCandidateConfig(expectedPort = 64342))
    assertTrue(client.isPortCorrect(expectedPort = 64342))
  }

  @Test
  fun `promotion fallback ignores localhost outside five thousand port range`() {
    val client = PromotionAwareMcpClient(
      linkedMapOf("fallback" to ExistingConfig(url = "http://localhost:69343/stream", type = "http"))
    )

    assertFalse(client.hasPromotionCandidateConfig(expectedPort = 64342))
    assertTrue(client.isPortCorrect(expectedPort = 64342))
  }
}

private class TestableMcpClient : McpClient(
  mcpClientInfo = McpClientInfo(name = McpClientInfo.Name.CURSOR, scope = McpClientInfo.Scope.Global),
  configPath = Paths.get("test.json")
) {
  fun buildUpdated(existing: JsonObject, entry: ServerConfig): JsonObject = buildUpdatedConfig(existing, entry)
}

private class TestableVSCodeClient : VSCodeClient(McpClientInfo.Scope.Global, Paths.get("vscode.json")) {
  fun buildUpdated(existing: JsonObject, entry: ServerConfig): JsonObject = buildUpdatedConfig(existing, entry)
}

private class PromotionAwareMcpClient(
  private val servers: Map<String, ExistingConfig>?,
) : McpClient(
  mcpClientInfo = McpClientInfo(name = McpClientInfo.Name.CURSOR, scope = McpClientInfo.Scope.Global),
  configPath = Paths.get("promotion.json")
) {
  override fun readMcpServers(): Map<String, ExistingConfig>? = servers
}
