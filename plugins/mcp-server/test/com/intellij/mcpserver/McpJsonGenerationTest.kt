package com.intellij.mcpserver

import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PORT
import com.intellij.mcpserver.stdio.IJ_MCP_SERVER_PROJECT_PATH
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpJsonGenerationTest {

  @Test
  fun test_createStdioMcpServerJsonConfiguration() {
    val port = 12345
    val projectPath = "/test/project/path"

    val jsonConfig = createStdioMcpServerJsonConfiguration(port, projectPath)

    // Verify the JSON structure
    Assertions.assertNotNull(jsonConfig["command"])
    Assertions.assertNotNull(jsonConfig["args"])
    Assertions.assertNotNull(jsonConfig["env"])

    // Verify command is a java executable
    val command = jsonConfig["command"]?.jsonPrimitive?.content
    assertTrue(command?.endsWith("java") == true, "Command should be java executable")

    // Verify environment variables
    val env = jsonConfig["env"]?.jsonObject!!
    Assertions.assertNotNull(env)
    Assertions.assertEquals(port.toString(), env[IJ_MCP_SERVER_PORT]?.jsonPrimitive?.content)
    Assertions.assertEquals(projectPath, env[IJ_MCP_SERVER_PROJECT_PATH]?.jsonPrimitive?.content)
  }

  @Test
  fun test_createStdioMcpServerJsonConfiguration_withoutProjectPath() {
    val port = 12345

    val jsonConfig = createStdioMcpServerJsonConfiguration(port, null)

    // Verify the JSON structure
    Assertions.assertNotNull(jsonConfig["command"])
    Assertions.assertNotNull(jsonConfig["args"])
    Assertions.assertNotNull(jsonConfig["env"])
    Assertions.assertEquals("stdio", jsonConfig["type"]?.jsonPrimitive?.content)

    // Verify environment variables
    val env = jsonConfig["env"]?.jsonObject!!
    Assertions.assertNotNull(env)
    Assertions.assertEquals(port.toString(), env[IJ_MCP_SERVER_PORT]?.jsonPrimitive?.content)
    // Project path should not be present when null
    assertTrue(env[IJ_MCP_SERVER_PROJECT_PATH] == null)
  }

  @Test
  fun test_createSseServerJsonEntry() {
    val port = 8080
    val projectPath = "/test/project/path"

    val jsonConfig = createSseServerJsonEntry(port, projectBasePath = projectPath)

    // Verify the JSON structure
    Assertions.assertEquals("sse", jsonConfig["type"]?.jsonPrimitive?.content)
    Assertions.assertEquals("http://localhost:$port/sse", jsonConfig["url"]?.jsonPrimitive?.content)
  }
}