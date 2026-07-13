// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.McpTool
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCategory
import com.intellij.mcpserver.McpToolDescriptor
import com.intellij.mcpserver.McpToolFilterProvider.McpToolFilterContext
import com.intellij.mcpserver.McpToolInvocationMode
import com.intellij.mcpserver.McpToolSchema
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import com.intellij.mcpserver.settings.McpToolDisallowListSettings.ToolState
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class DisallowListBasedMcpToolFilterProviderTest {
  private val provider = DisallowListBasedMcpToolFilterProvider()
  private val settings = McpToolDisallowListSettings.getInstance()

  @BeforeEach
  fun setUp() {
    settings.toolStates = emptyMap()
  }

  @AfterEach
  fun tearDown() {
    settings.toolStates = emptyMap()
  }

  @Test
  fun `applyFilters disables tool when state is stored by FQN`() {
    val tool = fakeTool(name = "read_file", fullName = "com.example.toolset.read_file")
    val context = McpToolFilterContext(listOf(tool))

    // Store by FQN (this is how the UI persists states)
    settings.toolStates = mapOf(
      "com.example.toolset.read_file" to ToolState(enabled = false, routerOnly = true),
    )

    provider.applyFilters(context, null, null, McpToolInvocationMode.DIRECT)

    assertThat(context.onTools).doesNotContain(tool)
    assertThat(context.routerOnlyTools).doesNotContain(tool)
  }

  @Test
  fun `applyFilters enables tool when state is stored by FQN with enabled=true and routerOnly=false`() {
    val tool = fakeTool(name = "read_file", fullName = "com.example.toolset.read_file")
    val context = McpToolFilterContext(listOf(tool))

    settings.toolStates = mapOf(
      "com.example.toolset.read_file" to ToolState(enabled = true, routerOnly = false),
    )

    provider.applyFilters(context, null, null, McpToolInvocationMode.DIRECT)

    assertThat(context.onTools).contains(tool)
    assertThat(context.routerOnlyTools).doesNotContain(tool)
  }

  @Test
  fun `applyFilters falls back to short name when FQN not stored`() {
    val tool = fakeTool(name = "read_file", fullName = "com.example.toolset.read_file")
    val context = McpToolFilterContext(listOf(tool))

    // Store only by short name (legacy state)
    settings.toolStates = mapOf(
      "read_file" to ToolState(enabled = false, routerOnly = true),
    )

    provider.applyFilters(context, null, null, McpToolInvocationMode.DIRECT)

    assertThat(context.onTools).doesNotContain(tool)
    assertThat(context.routerOnlyTools).doesNotContain(tool)
  }

  @Test
  fun `applyFilters handles multiple tools with same short name but different FQN`() {
    val toolA = fakeTool(name = "read_file", fullName = "com.example.toolsetA.read_file")
    val toolB = fakeTool(name = "read_file", fullName = "com.example.toolsetB.read_file")
    val context = McpToolFilterContext(listOf(toolA, toolB))

    // Disable only toolA by its FQN
    settings.toolStates = mapOf(
      "com.example.toolsetA.read_file" to ToolState(enabled = false, routerOnly = true),
    )

    provider.applyFilters(context, null, null, McpToolInvocationMode.DIRECT)

    assertThat(context.onTools).doesNotContain(toolA)
    assertThat(context.routerOnlyTools).doesNotContain(toolA)
    // toolB should remain at defaults (enabled=true, routerOnly=true)
    assertThat(context.onTools).doesNotContain(toolB)
    assertThat(context.routerOnlyTools).contains(toolB)
  }

  @Test
  fun `applyFilters sets routerOnly state correctly when stored by FQN`() {
    val tool = fakeTool(name = "read_file", fullName = "com.example.toolset.read_file")
    val context = McpToolFilterContext(listOf(tool))

    settings.toolStates = mapOf(
      "com.example.toolset.read_file" to ToolState(enabled = true, routerOnly = false),
    )

    provider.applyFilters(context, null, null, McpToolInvocationMode.DIRECT)

    // enabled=true, routerOnly=false means it should be in onTools
    assertThat(context.onTools).contains(tool)
    assertThat(context.routerOnlyTools).doesNotContain(tool)
  }

  private fun fakeTool(name: String, fullName: String): McpTool {
    return object : McpTool {
      override val descriptor = McpToolDescriptor(
        name = name,
        description = name,
        fullyQualifiedName = fullName,
        category = McpToolCategory(shortName = "Test", fullyQualifiedName = "test", isExperimental = false),
        inputSchema = McpToolSchema.ofPropertiesSchema(JsonObject(emptyMap()), emptySet(), emptyMap()),
      )
      override suspend fun call(args: JsonObject): McpToolCallResult = error("not needed")
      override fun toString(): String = "McpTool $fullName"
    }
  }
}
