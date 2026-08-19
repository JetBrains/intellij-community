@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.mcpserver.GeneralMcpToolsetTestBase
import com.intellij.mcpserver.toolsets.terminal.TerminalToolset
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
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
    val terminalToolset = TerminalToolset()
    withRegisteredTestTools(terminalToolset::execute_terminal_command) {
      testMcpTool(
        TerminalToolset::execute_terminal_command.name,
        buildJsonObject {
          put("command", JsonPrimitive("cat missingfile"))
        },
        """{"command_exit_code":1,"command_output":"cat: missingfile: No such file or directory\n"}"""
      )
    }
  }

  @Test
  fun execute_terminal_command_passes_configured_env_with_expanded_macro() = runBlocking(Dispatchers.Default) {
    TerminalProjectOptionsProvider.getInstance(project).setEnvData(
      EnvironmentVariablesData.create(mapOf("MCP_TEST_ENV_VAR" to $$"$PROJECT_DIR$/sub"), true))
    val terminalToolset = TerminalToolset()
    withRegisteredTestTools(terminalToolset::execute_terminal_command) {
      testMcpTool(
        TerminalToolset::execute_terminal_command.name,
        buildJsonObject {
          put("command", JsonPrimitive("printenv MCP_TEST_ENV_VAR"))
        },
      ) { result ->
        val text = result.textContent.text
        assertThat(text).contains("\"command_exit_code\":0")
        assertThat(text).contains("${project.basePath}/sub")
      }
    }
  }

  @Test
  fun execute_terminal_command_ignores_configured_env_for_untrusted_project() = runBlocking(Dispatchers.Default) {
    TerminalProjectOptionsProvider.getInstance(project).setEnvData(
      EnvironmentVariablesData.create(mapOf("MCP_TEST_ENV_VAR" to "value"), true))
    TrustedProjects.setProjectTrusted(project, false)
    try {
      val terminalToolset = TerminalToolset()
      withRegisteredTestTools(terminalToolset::execute_terminal_command) {
        testMcpTool(
          TerminalToolset::execute_terminal_command.name,
          buildJsonObject {
            put("command", JsonPrimitive("printenv MCP_TEST_ENV_VAR"))
          },
        ) { result ->
          // printenv exits with code 1 when the variable is not defined
          assertThat(result.textContent.text).contains("\"command_exit_code\":1")
        }
      }
    }
    finally {
      // the explicit trusted state is application-level and would leak to other tests
      TrustedProjects.setProjectTrusted(project, true)
    }
  }
}
