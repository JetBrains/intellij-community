// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestFunctionName")

package com.intellij.mcpserver

import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpToolDisallowListSettings
import com.intellij.mcpserver.statistics.McpServerCounterUsagesCollector
import com.intellij.mcpserver.settings.McpToolDisallowListSettings.ToolState
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.jetbrains.fus.reporting.api.ValidationResultType
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
          fakeTool("tool_c", "test.tool_c"),
          fakeTool("tool_direct", "test.tool_direct", alwaysIncluded = true)
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
    assertThat(names).contains("tool_a", "tool_b", "tool_c", "tool_direct", ROUTER_TOOL)
  }

  @Test
  fun `getMcpTools in DIRECT_WITH_ROUTER_ENABLED mode returns enabled tools and router`() {
    val tools = McpServerService.getInstance().getMcpTools(
      invocationMode = McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED
    )

    val names = tools.map { it.descriptor.name }.toSet()
    assertThat(names)
      .contains("tool_c", "tool_direct", ROUTER_TOOL)
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
      .doesNotContain("tool_c", "tool_direct")
  }

  @Test
  fun `DIRECT mode omits the unused router when the session opts in`() {
    val names = toolNames(McpToolInvocationMode.DIRECT, omittingRouterOptions())

    assertThat(names)
      .contains("tool_a", "tool_b", "tool_c", "tool_direct")
      .doesNotContain(ROUTER_TOOL)
  }

  @Test
  fun `DIRECT_WITH_ROUTER_ENABLED mode lists the router for a router-only tool when the session opts in`() {
    val names = toolNames(McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED, omittingRouterOptions())

    assertThat(names)
      .contains("tool_c", "tool_direct", ROUTER_TOOL)
      .doesNotContain("tool_a", "tool_b")
  }

  @Test
  fun `DIRECT_WITH_ROUTER_ENABLED mode omits the router when the mask leaves no router-only tool`() {
    val filter = McpToolFilter.AllowList(setOf("tool_c", "tool_direct"))

    assertThat(toolNames(McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED, omittingRouterOptions(), filter))
      .containsExactlyInAnyOrder("tool_c", "tool_direct")
    assertThat(toolNames(McpToolInvocationMode.DIRECT_WITH_ROUTER_ENABLED, sessionOptions = null, filter))
      .describedAs("a session without the opt-in keeps the router")
      .containsExactlyInAnyOrder("tool_c", "tool_direct", ROUTER_TOOL)
  }

  @Test
  fun `VIA_ROUTER mode omits the router when the session opts in`() {
    val names = toolNames(McpToolInvocationMode.VIA_ROUTER, omittingRouterOptions())

    assertThat(names)
      .contains("tool_a", "tool_b")
      .doesNotContain("tool_c", "tool_direct", ROUTER_TOOL)
  }

  @Test
  fun `the path without filter providers omits the router when the session opts in`() {
    val tools = McpServerService.getInstance().getMcpTools(
      filter = McpToolFilter.AllowList(setOf("tool_a")),
      useFiltersFromEP = false,
      sessionOptions = omittingRouterOptions(),
    )

    assertThat(tools.map { it.descriptor.name }).containsExactly("tool_a")
  }

  private fun omittingRouterOptions(): McpServerService.McpSessionOptions =
    McpServerService.McpSessionOptions(McpServerService.AskCommandExecutionMode.DONT_ASK).withUnusedRouterToolOmitted()

  private fun toolNames(
    invocationMode: McpToolInvocationMode,
    sessionOptions: McpServerService.McpSessionOptions?,
    filter: McpToolFilter? = null,
  ): List<String> {
    return McpServerService.getInstance()
      .getMcpTools(filter = filter, sessionOptions = sessionOptions, invocationMode = invocationMode)
      .map { it.descriptor.name }
  }

  /**
   * The toolset a call belongs to is reported, and the value set is derived from the tools actually registered rather
   * than from a list someone maintains — so a new toolset is measurable the day it is added, and no unregistered name
   * can pass. This is what makes "measure every toolset" a property of the code instead of a chore.
   */
  @Test
  fun `a registered toolset is reportable and an unknown one is refused`() {
    val validator = McpServerCounterUsagesCollector.McpToolsetNameValidator()

    assertThat(validator.validate("test", EventContext.create("toolset", emptyMap())))
      .describedAs("the fake provider registered tools in the 'test' toolset, so its name must resolve")
      .isIn(ValidationResultType.ACCEPTED, ValidationResultType.THIRD_PARTY)
    assertThat(validator.validate("not-a-registered-toolset", EventContext.create("toolset", emptyMap())))
      .isEqualTo(ValidationResultType.REJECTED)
  }

  private fun fakeTool(name: String, fullName: String, alwaysIncluded: Boolean = false): McpTool {
    return object : McpTool {
      override val descriptor = McpToolDescriptor(
        name = name,
        description = name,
        fullyQualifiedName = fullName,
        category = McpToolCategory(shortName = "Test", fullyQualifiedName = "test", isExperimental = false,
                                   alwaysIncluded = alwaysIncluded),
        inputSchema = McpToolSchema.ofPropertiesSchema(JsonObject(emptyMap()), emptySet(), emptyMap()),
      )

      override suspend fun call(args: JsonObject): McpToolCallResult = error("not needed")
      override fun toString(): String = "McpTool $name"
    }
  }
}
