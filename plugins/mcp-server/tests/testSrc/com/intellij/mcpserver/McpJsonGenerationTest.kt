package com.intellij.mcpserver

import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class McpJsonGenerationTest {

  @Test
  fun test_createStdioMcpServerJsonConfiguration() {
    val port = 12345
    val projectPath = "/test/project/path"

    val jsonConfig = createStdioMcpServerJsonConfiguration(port, projectPath)

    // Verify the JSON structure
    assertThat(jsonConfig).containsKeys("command", "args", "env")

    // Verify command is a java executable
    val command = jsonConfig["command"]!!.jsonPrimitive.content
    assertThat(command).endsWith("java")

    // Verify environment variables
    val env = jsonConfig["env"]!!.jsonObject
    assertThat(env[IJ_MCP_SERVER_PORT]?.jsonPrimitive?.content).isEqualTo(port.toString())
    assertThat(env[IJ_MCP_SERVER_PROJECT_PATH]?.jsonPrimitive?.content).isEqualTo(projectPath)
  }

  @Test
  fun test_createStdioMcpServerJsonConfiguration_withoutProjectPath() {
    val port = 12345

    val jsonConfig = createStdioMcpServerJsonConfiguration(port, null)

    // Verify the JSON structure
    assertThat(jsonConfig).containsKeys("command", "args", "env")
    assertThat(jsonConfig["type"]!!.jsonPrimitive.content).isEqualTo("stdio")

    // Verify environment variables
    val env = jsonConfig["env"]!!.jsonObject
    assertThat(env[IJ_MCP_SERVER_PORT]?.jsonPrimitive?.content).isEqualTo(port.toString())
    // Project path should not be present when null
    assertThat(env).doesNotContainKey(IJ_MCP_SERVER_PROJECT_PATH)
  }

  @Test
  fun test_createSseServerJsonEntry() {
    val port = 8080
    val projectPath = "/test/project/path"

    val jsonConfig = createSseServerJsonEntry(port, projectBasePath = projectPath)

    // Verify the JSON structure
    assertThat(jsonConfig["type"]!!.jsonPrimitive.content).isEqualTo("sse")
    val expectedUrls = setOf("http://127.0.0.1:$port/sse", "http://localhost:$port/sse", "http://[localhost]:$port/sse")
    assertThat(expectedUrls).contains(jsonConfig["url"]!!.jsonPrimitive.content)
  }
}
