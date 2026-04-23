// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.DisallowListBasedMcpToolFilterProvider
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import com.intellij.mcpserver.settings.McpToolDisallowListSettings.ToolState
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable

@Testable
@TestApplication
class McpToolDisallowListSettingsTest {
  @AfterEach
  fun tearDown() {
    McpToolDisallowListSettings.getInstance().toolStates = emptyMap()
  }

  @Test
  fun `legacy enum states migrate to enabled and on demand booleans`() {
    val settings = McpToolDisallowListSettings.getInstance()
    val state = McpToolDisallowListSettings.MyState().apply {
      legacyToolStates["enabled"] = McpToolFilterProvider.McpToolState.ON
      legacyToolStates["onDemand"] = McpToolFilterProvider.McpToolState.ON_DEMAND
      legacyToolStates["disabled"] = McpToolFilterProvider.McpToolState.OFF
    }

    settings.loadState(state)

    assertThat(settings.toolStates).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "enabled" to ToolState(enabled = true, onDemand = false),
        "onDemand" to ToolState(enabled = true, onDemand = true),
        "disabled" to ToolState(enabled = false, onDemand = false),
      ),
    )
  }

  @Test
  fun `tool states preserve all enabled and on demand combinations`() {
    val toolStates = mapOf(
      "enabled_tool" to ToolState(enabled = true, onDemand = false),
      "on_demand_tool" to ToolState(enabled = true, onDemand = true),
      "disabled_tool" to ToolState(enabled = false, onDemand = false),
      "disabled_on_demand_tool" to ToolState(enabled = false, onDemand = true),
    )
    val settings = McpToolDisallowListSettings.getInstance()

    settings.toolStates = toolStates

    assertThat(settings.toolStates).containsExactlyInAnyOrderEntriesOf(toolStates)
  }

  @Test
  fun `provider maps all enabled and on demand combinations to runtime states`() {
    data class ToolExpectation(
      val name: String,
      val toolState: ToolState,
      val expectedOn: Boolean,
      val expectedOnDemand: Boolean,
    )

    val expectations = listOf(
      ToolExpectation(
        name = "enabled_tool",
        toolState = ToolState(enabled = true, onDemand = false),
        expectedOn = true,
        expectedOnDemand = false,
      ),
      ToolExpectation(
        name = "on_demand_tool",
        toolState = ToolState(enabled = true, onDemand = true),
        expectedOn = false,
        expectedOnDemand = true,
      ),
      ToolExpectation(
        name = "disabled_tool",
        toolState = ToolState(enabled = false, onDemand = false),
        expectedOn = false,
        expectedOnDemand = false,
      ),
      ToolExpectation(
        name = "disabled_on_demand_tool",
        toolState = ToolState(enabled = false, onDemand = true),
        expectedOn = false,
        expectedOnDemand = false,
      ),
    )
    McpToolDisallowListSettings.getInstance().toolStates = expectations.associate { it.name to it.toolState }

    val context = McpToolFilterProvider.McpToolFilterContext(expectations.map { testTool(it.name) })

    DisallowListBasedMcpToolFilterProvider().applyFilters(
      context = context,
      clientInfo = null,
      sessionOptions = null,
      invocationMode = McpToolInvocationMode.DIRECT,
    )

    val onTools = context.onTools.map { it.descriptor.name }.toSet()
    val onDemandTools = context.onDemandTools.map { it.descriptor.name }.toSet()
    assertThat(onTools).containsExactly("enabled_tool")
    assertThat(onDemandTools).containsExactly("on_demand_tool")

    expectations.forEach { expectation ->
      assertThat(onTools.contains(expectation.name))
        .describedAs("Unexpected direct state for %s", expectation.name)
        .isEqualTo(expectation.expectedOn)
      assertThat(onDemandTools.contains(expectation.name))
        .describedAs("Unexpected on-demand state for %s", expectation.name)
        .isEqualTo(expectation.expectedOnDemand)
    }
  }

  private fun testTool(name: String, experimental: Boolean = false): McpTool {
    return object : McpTool {
      override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = name,
        category = McpToolCategory(
          shortName = if (experimental) "Experimental" else "Stable",
          fullyQualifiedName = "test.${if (experimental) "experimental" else "stable"}",
          isExperimental = experimental,
        ),
        fullyQualifiedName = "test.$name",
        inputSchema = McpToolSchema.ofPropertiesSchema(buildJsonObject { }, emptySet(), emptyMap()),
      )

      override suspend fun call(args: kotlinx.serialization.json.JsonObject): McpToolCallResult {
        error("Not needed for tests")
      }
    }
  }
}
