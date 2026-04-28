// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.DisallowListBasedMcpToolFilterProvider
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import com.intellij.mcpserver.settings.McpToolDisallowListSettings.ToolState
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.JsonObject
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
  fun `legacy enum states migrate to enabled and router only booleans`() {
    val settings = McpToolDisallowListSettings.getInstance()
    val state = McpToolDisallowListSettings.MyState().apply {
      legacyToolStates["enabled"] = McpToolDisallowListSettings.MyState.LegacyMcpToolState.ON
      legacyToolStates["routerOnly"] = McpToolDisallowListSettings.MyState.LegacyMcpToolState.ON_DEMAND
      legacyToolStates["disabled"] = McpToolDisallowListSettings.MyState.LegacyMcpToolState.OFF
    }

    settings.loadState(state)

    assertThat(settings.toolStates).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "enabled" to ToolState(enabled = true, routerOnly = false),
        "routerOnly" to ToolState(enabled = true, routerOnly = true),
        "disabled" to ToolState(enabled = false, routerOnly = false),
      ),
    )
  }

  @Test
  fun `tool states preserve all enabled and router only combinations`() {
    val toolStates = mapOf(
      "enabled_tool" to ToolState(enabled = true, routerOnly = false),
      "router_only_tool" to ToolState(enabled = true, routerOnly = true),
      "disabled_tool" to ToolState(enabled = false, routerOnly = false),
      "disabled_router_only_tool" to ToolState(enabled = false, routerOnly = true),
    )
    val settings = McpToolDisallowListSettings.getInstance()

    settings.toolStates = toolStates

    assertThat(settings.toolStates).containsExactlyInAnyOrderEntriesOf(toolStates)
  }

  @Test
  fun `provider maps all enabled and router only combinations to runtime states`() {
    data class ToolExpectation(
      val name: String,
      val toolState: ToolState,
      val expectedOn: Boolean,
      val expectedRouterOnly: Boolean,
    )

    val expectations = listOf(
      ToolExpectation(
        name = "enabled_tool",
        toolState = ToolState(enabled = true, routerOnly = false),
        expectedOn = true,
        expectedRouterOnly = false,
      ),
      ToolExpectation(
        name = "router_only_tool",
        toolState = ToolState(enabled = true, routerOnly = true),
        expectedOn = false,
        expectedRouterOnly = true,
      ),
      ToolExpectation(
        name = "disabled_tool",
        toolState = ToolState(enabled = false, routerOnly = false),
        expectedOn = false,
        expectedRouterOnly = false,
      ),
      ToolExpectation(
        name = "disabled_router_only_tool",
        toolState = ToolState(enabled = false, routerOnly = true),
        expectedOn = false,
        expectedRouterOnly = false,
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
    val routerOnlyTools = context.routerOnlyTools.map { it.descriptor.name }.toSet()
    assertThat(onTools).containsExactly("enabled_tool")
    assertThat(routerOnlyTools).containsExactly("router_only_tool")

    expectations.forEach { expectation ->
      assertThat(onTools.contains(expectation.name))
        .describedAs("Unexpected direct state for %s", expectation.name)
        .isEqualTo(expectation.expectedOn)
      assertThat(routerOnlyTools.contains(expectation.name))
        .describedAs("Unexpected router-only state for %s", expectation.name)
        .isEqualTo(expectation.expectedRouterOnly)
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

      override suspend fun call(args: JsonObject): McpToolCallResult {
        error("Not needed for tests")
      }
    }
  }
}
