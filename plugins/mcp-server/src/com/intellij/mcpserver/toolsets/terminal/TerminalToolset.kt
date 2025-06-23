@file:Suppress("FunctionName", "unused", "PropertyName")

package com.intellij.mcpserver.toolsets.terminal

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalView
import javax.swing.JComponent
import kotlin.time.Duration.Companion.minutes

private val logger = logger<TerminalToolset>()

class TerminalToolset : McpToolset {
  private val maxLineCount = 2000
  private val timeout = 2.minutes

  @McpTool
  @McpDescription("""
        Retrieves the current text content from the first active terminal in the IDE.
        Use this tool to access the terminal's output and command history.
        Returns one of two possible responses:
        - The terminal's text content if a terminal exists
        - empty string if no terminal is open or available
        Note: Only captures text from the first terminal if multiple terminals are open
    """)
  suspend fun get_terminal_text(): String {
    val project = currentCoroutineContext().project
    val text = runReadAction<String?> {
      val terminalWidget = TerminalView.getInstance(project).getWidgets().firstOrNull() ?: throw McpExpectedError("No terminal available")
      terminalWidget.text
    }
    return text ?: ""
  }

  @McpTool
  @McpDescription("""
        Executes a specified shell command in the IDE's integrated terminal.
        Use this tool to run terminal commands within the IDE environment.
        Requires a command parameter containing the shell command to execute.
        Important features and limitations:
        - Checks if process is running before collecting output
        - Limits output to 2000 lines (truncates excess)
        - Times out after 120000 milliseconds with notification
        - Requires user confirmation unless "Brave Mode" is enabled in settings
        Returns possible responses:
        - Terminal output (truncated if > 2000 lines)
        - Output with interruption notice if timed out
        - Error messages for various failure cases
    """)
  suspend fun execute_terminal_command(
    @McpDescription("Shell command to execute")
    command: String,

    @McpDescription("""Whether to execute the command in a default user's shell (bash, zsh, etc.). 
      |Useful if the command is not a commandline but a shell script, or if it's important to preserve real environment of the user's terminal. 
      |In the case of 'false' value the command will be started as a process""")
    execute_in_shell: Boolean = false,

    @McpDescription("Whether to reuse an existing terminal window. Allows to avoid creating multiple terminals")
    reuse_existing_terminal_window: Boolean = true,

    @McpDescription("Timeout for command execution in milliseconds")
    timeout_milliseconds: Int = this.timeout.inWholeMilliseconds.toInt(),
  ): CommandExecutionResult {
    val project = currentCoroutineContext().project
    if (!McpServerSettings.getInstance().state.enableBraveMode && !askConfirmation(project, command)) throw McpExpectedError("User rejected command execution")

    // TODO pass from http request later (MCP Client name or something else)
    val id = "mcp_session"
    val window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
    return executeShellCommand(window = window, project = project, command = command, executeInShell = execute_in_shell, sessionId = if (reuse_existing_terminal_window) id else null, timeout = timeout)
  }

  @OptIn(ExperimentalSerializationApi::class)
  @Serializable
  class CommandExecutionResult(
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val is_timed_out: Boolean? = null,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val command_exit_code: Int? = null,
    val command_output: String)
}