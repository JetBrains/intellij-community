package com.intellij.mcpserver

import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamableConfigJsonTest {
  @Test
  fun `streamable config omits project header when path not provided`() {
    val json = createStreamableServerJsonEntry(port = 64343, projectBasePath = null)
    val headers = json["headers"]!!.jsonObject

    assertFalse(headers.containsKey(IJ_MCP_SERVER_PROJECT_PATH))
    assertEquals("streamable-http", json["type"]!!.jsonPrimitive.content)
    assertEquals("http://localhost:64343/stream", json["url"]!!.jsonPrimitive.content)
  }

  @Test
  fun `streamable config includes project header when path provided`() {
    val json = createStreamableServerJsonEntry(port = 64343, projectBasePath = "/project")
    val headers = json["headers"]!!.jsonObject

    assertTrue(headers.containsKey(IJ_MCP_SERVER_PROJECT_PATH))
    assertEquals("/project", headers[IJ_MCP_SERVER_PROJECT_PATH]!!.jsonPrimitive.content)
  }
}
