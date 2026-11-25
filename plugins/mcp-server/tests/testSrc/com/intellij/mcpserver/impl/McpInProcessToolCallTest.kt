// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.impl

import com.intellij.mcpserver.ClientInfo
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolCallResultContent
import com.intellij.mcpserver.McpToolFilter
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.elicitation.buildElicitationForm
import com.intellij.mcpserver.elicitation.elicitationProvider
import com.intellij.mcpserver.setRouterControls
import com.intellij.mcpserver.testFramework.withRegisteredTestTools
import com.intellij.mcpserver.toolsets.general.UniversalToolset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [McpServerService.callTool] runs a tool of a session in this process, with no server and no MCP session. */
class McpInProcessToolCallTest : GeneralMcpToolsetTestBase() {
  private var routerControls: AutoCloseable? = null

  @BeforeEach
  fun enableRouter() {
    routerControls = setRouterControls(true)
  }

  @AfterEach
  fun restoreRouter() {
    routerControls?.close()
  }

  @Test
  fun the_router_reaches_a_tool_of_the_allow_list(): Unit = runBlocking(Dispatchers.Default) {
    val result = call(UniversalToolset::execute_tool.name, command("reformat_file --files '[\"src/Main.java\"]'"))

    assertThat(result.isError).isFalse()
    assertThat(result.text()).isEqualTo("ok")
  }

  @Test
  fun the_router_reaches_no_tool_outside_the_allow_list(): Unit = runBlocking(Dispatchers.Default) {
    val result = call(UniversalToolset::execute_tool.name, command("search_file --q Main.java"))

    assertThat(result.isError).isTrue()
    assertThat(result.text()).contains("Tool 'search_file' not found")
  }

  @Test
  fun a_tool_that_the_session_does_not_list_gives_an_error_result(): Unit = runBlocking(Dispatchers.Default) {
    val result = call("search_file", buildJsonObject { put("q", JsonPrimitive("Main.java")) })

    assertThat(result.isError).isTrue()
    assertThat(result.text()).isEqualTo("Tool 'search_file' not found")
  }

  /** A routed tool runs only through the router, as in a session, which does not list it. */
  @Test
  fun a_routed_tool_does_not_run_by_its_own_name(): Unit = runBlocking(Dispatchers.Default) {
    val result = call("reformat_file", buildJsonObject { put("files", JsonArray(listOf(JsonPrimitive("src/Main.java")))) })

    assertThat(result.isError).isTrue()
    assertThat(result.text()).isEqualTo("Tool 'reformat_file' not found")
  }

  /** The caller of an in-process call asks no user, so an elicitation of its tool fails the call with a clear text. */
  @Test
  fun an_elicitation_of_an_in_process_call_fails_the_call(): Unit = runBlocking(Dispatchers.Default) {
    withRegisteredTestTools(this@McpInProcessToolCallTest::asks_the_user) {
      val options = McpServerService.McpSessionOptions(
        commandExecutionMode = McpServerService.AskCommandExecutionMode.DONT_ASK,
        toolFilter = McpToolFilter.AllowList(setOf("asks_the_user")),
        localAgentId = null,
        invocationMode = McpSessionInvocationMode.DIRECT,
      )
      val result = McpServerService.getInstanceAsync().callTool(options, checkNotNull(project.basePath), CALLER, "asks_the_user", JsonObject(emptyMap()))

      assertThat(result.isError).isTrue()
      assertThat(result.text()).contains("asks no user")
    }
  }

  @McpTool(title = "Asks the user")
  @McpDescription("Asks the user one question.")
  suspend fun asks_the_user(): String {
    val provider = elicitationProvider() ?: return "no question asked"
    provider.requestElicitation(buildElicitationForm { message { text("Go on?") } }, serializer<String>())
    return "answered"
  }

  private suspend fun call(toolName: String, arguments: JsonObject): McpToolCallResult {
    val options = McpServerService.McpSessionOptions(
      commandExecutionMode = McpServerService.AskCommandExecutionMode.DONT_ASK,
      toolFilter = McpToolFilter.AllowList(setOf("reformat_file")),
      localAgentId = null,
      invocationMode = McpSessionInvocationMode.VIA_ROUTER,
    )
    val projectPath = checkNotNull(project.basePath)
    return McpServerService.getInstanceAsync().callTool(options, projectPath, CALLER, toolName, arguments)
  }
}

private val CALLER = ClientInfo("test caller", "1.0")

private fun command(command: String): JsonObject = buildJsonObject { put("command", JsonPrimitive(command)) }

private fun McpToolCallResult.text(): String = content.joinToString("") { (it as McpToolCallResultContent.Text).text }
