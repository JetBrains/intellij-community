@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.platform.commons.annotation.Testable
import kotlin.coroutines.EmptyCoroutineContext

@Testable
@TestApplication
class ProjectResolutionTest {
  companion object {
    @BeforeAll
    @JvmStatic
    fun init() {
      System.setProperty("java.awt.headless", "false")
    }
  }

  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project by projectFixture

  @Test
  fun test_getProjectByNameOrPath_with_valid_name() = runTest {
    // Create a mock MCP call context
    val callInfo = McpCallInfo(
      callId = 1,
      clientInfo = ClientInfo("test", "1.0"),
      project = project,
      mcpToolDescriptor = McpToolDescriptor("test", "test", McpToolSchema.ofPropertiesMap(emptyMap(), emptySet(), emptyMap())),
      rawArguments = kotlinx.serialization.json.buildJsonObject { },
      meta = kotlinx.serialization.json.buildJsonObject { }
    )

    val context = EmptyCoroutineContext + McpCallAdditionalDataElement(callInfo)

    // Test with valid project name
    val resolvedProject = context.getProjectByNameOrPathOrNull(project.name, null)
    assert(resolvedProject == project) { "Should resolve to the correct project" }
  }

  @Test
  fun test_getProjectByNameOrPath_with_invalid_name() = runTest {
    // Create a mock MCP call context
    val callInfo = McpCallInfo(
      callId = 1,
      clientInfo = ClientInfo("test", "1.0"),
      project = project,
      mcpToolDescriptor = McpToolDescriptor("test", "test", McpToolSchema.ofPropertiesMap(emptyMap(), emptySet(), emptyMap())),
      rawArguments = kotlinx.serialization.json.buildJsonObject { },
      meta = kotlinx.serialization.json.buildJsonObject { }
    )

    val context = EmptyCoroutineContext + McpCallAdditionalDataElement(callInfo)

    // Test with invalid project name
    val resolvedProject = context.getProjectByNameOrPathOrNull("nonexistent-project", null)
    assert(resolvedProject == null) { "Should return null for nonexistent project" }
  }

  @Test
  fun test_getProjectByNameOrPath_throws_for_invalid_name() = runTest {
    // Create a mock MCP call context
    val callInfo = McpCallInfo(
      callId = 1,
      clientInfo = ClientInfo("test", "1.0"),
      project = project,
      mcpToolDescriptor = McpToolDescriptor("test", "test", McpToolSchema.ofPropertiesMap(emptyMap(), emptySet(), emptyMap())),
      rawArguments = kotlinx.serialization.json.buildJsonObject { },
      meta = kotlinx.serialization.json.buildJsonObject { }
    )

    val context = EmptyCoroutineContext + McpCallAdditionalDataElement(callInfo)

    // Test that getProjectByNameOrPath throws for invalid project name
    assertThrows<ProjectNotFoundException> {
      context.getProjectByNameOrPath("nonexistent-project", null)
    }
  }

  @Test
  fun test_getProjectByNameOrPath_with_null_name_falls_back() = runTest {
    // Create a mock MCP call context
    val callInfo = McpCallInfo(
      callId = 1,
      clientInfo = ClientInfo("test", "1.0"),
      project = project,
      mcpToolDescriptor = McpToolDescriptor("test", "test", McpToolSchema.ofPropertiesMap(emptyMap(), emptySet(), emptyMap())),
      rawArguments = kotlinx.serialization.json.buildJsonObject { },
      meta = kotlinx.serialization.json.buildJsonObject { }
    )

    val context = EmptyCoroutineContext + McpCallAdditionalDataElement(callInfo)

    // Test with null project name should fall back to context project
    val resolvedProject = context.getProjectByNameOrPathOrNull(null, null)
    assert(resolvedProject == project) { "Should fall back to context project when name is null" }
  }

  @Test
  fun test_getProjectByNameOrPath_with_valid_path() = runTest {
    // Create a mock MCP call context
    val callInfo = McpCallInfo(
      callId = 1,
      clientInfo = ClientInfo("test", "1.0"),
      project = project,
      mcpToolDescriptor = McpToolDescriptor("test", "test", McpToolSchema.ofPropertiesMap(emptyMap(), emptySet(), emptyMap())),
      rawArguments = kotlinx.serialization.json.buildJsonObject { },
      meta = kotlinx.serialization.json.buildJsonObject { }
    )

    val context = EmptyCoroutineContext + McpCallAdditionalDataElement(callInfo)

    // Test with valid project path
    val resolvedProject = context.getProjectByNameOrPathOrNull(null, project.basePath)
    assert(resolvedProject == project) { "Should resolve to the correct project by path" }
  }
}
