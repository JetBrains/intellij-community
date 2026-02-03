package com.intellij.mcpserver.clients

import com.intellij.mcpserver.clients.configs.STDIOServerConfig
import com.intellij.mcpserver.clients.configs.ServerConfig
import com.intellij.mcpserver.clients.configs.VSCodeSSEConfig
import com.intellij.mcpserver.clients.impl.VSCodeClient
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
    val serverEntry = VSCodeSSEConfig(url = "http://localhost:1234/sse", type = "sse")

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
    val serverEntry = VSCodeSSEConfig(url = "http://localhost:7777/sse", type = "sse")

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
}

private class TestableMcpClient : McpClient(
  mcpClientInfo = McpClientInfo(name = McpClientInfo.Name.CURSOR, scope = McpClientInfo.Scope.GLOBAL),
  configPath = Paths.get("test.json")
) {
  fun buildUpdated(existing: JsonObject, entry: ServerConfig): JsonObject = buildUpdatedConfig(existing, entry)
}

private class TestableVSCodeClient : VSCodeClient(McpClientInfo.Scope.GLOBAL, Paths.get("vscode.json")) {
  fun buildUpdated(existing: JsonObject, entry: ServerConfig): JsonObject = buildUpdatedConfig(existing, entry)
}

