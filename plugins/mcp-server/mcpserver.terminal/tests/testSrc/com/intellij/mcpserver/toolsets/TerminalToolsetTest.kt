@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.terminal.TerminalToolset
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TerminalToolsetTest : GeneralMcpToolsetTestBase() {

  @BeforeEach
  fun init() {
    val toolWindow = (ToolWindowManager.getInstance(project) as ToolWindowHeadlessManagerImpl).doRegisterToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
    TerminalToolWindowFactory().createToolWindowContent(project, toolWindow)
  }

  @Test
  fun execute_terminal_command() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      TerminalToolset::execute_terminal_command.name,
      buildJsonObject {
        put("command", JsonPrimitive("cat missingfile"))
      },
      """{"command_exit_code":1,"command_output":"cat: missingfile: No such file or directory\n"}"""
    )
  }
}
