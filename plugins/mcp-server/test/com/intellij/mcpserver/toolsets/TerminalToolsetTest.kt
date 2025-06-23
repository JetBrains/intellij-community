@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.terminal.TerminalToolset
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import io.kotest.common.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.junit.Before
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.BeforeTest

class TerminalToolsetTest : McpToolsetTestBase() {

  @BeforeEach
  fun init() {
    val toolWindow = (ToolWindowManager.getInstance(project) as ToolWindowHeadlessManagerImpl).doRegisterToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
    TerminalToolWindowFactory().createToolWindowContent(project, toolWindow)
  }

  @Test
  fun get_terminal_text() = runBlocking {
    testMcpTool(
      TerminalToolset::get_terminal_text.name,
      buildJsonObject {},
      "No terminal available"
    )
  }

  @Test
  fun execute_terminal_command() = runBlocking {
    testMcpTool(
      TerminalToolset::execute_terminal_command.name,
      buildJsonObject {
        put("command", JsonPrimitive("cat missingfile"))
      },
      """{"command_exit_code":1,"command_output":"cat: missingfile: No such file or directory\n"}"""
    )
  }
}