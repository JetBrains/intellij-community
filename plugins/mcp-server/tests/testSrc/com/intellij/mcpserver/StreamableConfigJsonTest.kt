package com.intellij.mcpserver

import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StreamableConfigJsonTest {
  @Test
  fun `streamable config omits project header when path not provided`() {
    val port = 64343
    val json = createStreamableServerJsonEntry(port = port, projectBasePath = null)
    val headers = json["headers"]!!.jsonObject

    assertThat(headers).doesNotContainKey(IJ_MCP_SERVER_PROJECT_PATH)
    assertThat(json["type"]!!.jsonPrimitive.content).isEqualTo("streamable-http")
    val expectedUrls = setOf("http://localhost:$port/stream", "http://127.0.0.1:$port/stream")
    assertThat(expectedUrls).contains(json["url"]!!.jsonPrimitive.content)
  }

  @Test
  fun `streamable config includes project header when path provided`() {
    val json = createStreamableServerJsonEntry(port = 64343, projectBasePath = "/project")
    val headers = json["headers"]!!.jsonObject

    assertThat(headers).containsKey(IJ_MCP_SERVER_PROJECT_PATH)
    assertThat(headers[IJ_MCP_SERVER_PROJECT_PATH]!!.jsonPrimitive.content).isEqualTo("/project")
  }
}
