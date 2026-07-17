// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import com.intellij.mcpserver.settings.McpToolDisallowListSettings.ToolState
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.platform.commons.annotation.Testable
import kotlin.time.Duration.Companion.seconds

private const val ROUTER_TOOL = "execute_tool"

/**
 * Tests [McpServerService.getMcpTools] with different [McpToolInvocationMode] values.
 * Uses a mocked tool provider so exact tool counts are deterministic.
 */
@Testable
@TestApplication
class McpToolInvocationModeTest {

  private lateinit var disposable: Disposable
  private val settings = McpToolDisallowListSettings.getInstance()

  @BeforeEach
  fun setUp() {
    disposable = Disposer.newDisposable()
    settings.toolStates = emptyMap()
    application.extensionArea.getExtensionPoint(McpToolsProvider.EP).registerExtension(
      object : McpToolsProvider {
        override fun getTools(): List<McpTool> = listOf(
          fakeTool("tool_a", "test.tool_a"),
          fakeTool("tool_b", "test.tool_b"),
          fakeTool("tool_c", "test.tool_c")
        )
      },
      disposable
    )
    settings.toolStates = mapOf(
      "test.tool_c" to ToolState(enabled = true, routerOnly = false)
    )
    // Allow the tools flow to update
    runBlocking { delay(1.seconds) }
  }

  @AfterEach
  fun tearDown() {
    settings.toolStates = emptyMap()
    Disposer.dispose(disposable)
  }

  @Test
  fun `getMcpTools in DIRECT mode returns all fake tools plus router`() {
    val tools = McpServerService.getInstance().getMcpTools(
      invocationMode = McpToolInvocationMode.DIRECT
    )

    val names = tools.map { it.descriptor.name }.toSet()
    assertThat(names).contains("tool_a", "tool_b", "tool_c", ROUTER_TOOL)
  }

  @Test
  fun `getMcpTools in DIRECT_WITH_ROUTER_ENABLED mode returns enabled tools and router`() {
    val tools = McpServerService.getInstance().getMcpTools(
      invocationMode = McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED
    )

    val names = tools.map { it.descriptor.name }.toSet()
    assertThat(names)
      .contains("tool_c", ROUTER_TOOL)
      .doesNotContain("tool_a", "tool_b")
  }

  @Test
  fun `getMcpTools in VIA_ROUTER mode returns only router only tools and router`() {
    val tools = McpServerService.getInstance().getMcpTools(
      invocationMode = McpToolInvocationMode.VIA_ROUTER
    )

    val names = tools.map { it.descriptor.name }.toSet()
    assertThat(names)
      .contains("tool_a", "tool_b", ROUTER_TOOL)
      .doesNotContain("tool_c")
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
      override fun toString(): String = "McpTool $name"
    }
  }
}
