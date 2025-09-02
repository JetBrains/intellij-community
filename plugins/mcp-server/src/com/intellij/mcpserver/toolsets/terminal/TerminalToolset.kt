@file:Suppress("FunctionName", "unused", "PropertyName")

package com.intellij.mcpserver.toolsets.terminal

import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.toolsets.Constants
import com.intellij.mcpserver.util.TruncateMode
import com.intellij.mcpserver.util.checkUserConfirmationIfNeeded
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import kotlin.time.Duration.Companion.milliseconds

private val logger = logger<TerminalToolset>()

class TerminalToolset : McpToolset {
  @McpTool
  @McpDescription("""
        Executes a specified shell command in the IDE's integrated terminal.
        Use this tool to run terminal commands within the IDE environment.
        Requires a command parameter containing the shell command to execute.
        Important features and limitations:
        - Checks if process is running before collecting output
        - Limits output to 2000 lines (truncates excess)
        - Times out after specified timeout with notification
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
    executeInShell: Boolean = false,
    @McpDescription("Whether to reuse an existing terminal window. Allows to avoid creating multiple terminals")
    reuseExistingTerminalWindow: Boolean = true,
    @McpDescription(Constants.TIMEOUT_MILLISECONDS_DESCRIPTION)
    timeout: Int = Constants.LONG_TIMEOUT_MILLISECONDS_VALUE,
    @McpDescription(Constants.MAX_LINES_COUNT_DESCRIPTION)
    maxLinesCount: Int = Constants.MAX_LINES_COUNT_VALUE,
    @McpDescription(Constants.TRUNCATE_MODE_DESCRIPTION)
    truncateMode: TruncateMode = Constants.TRUCATE_MODE_VALUE,
  ): CommandExecutionResult {
    val project = currentCoroutineContext().project
    checkUserConfirmationIfNeeded(McpServerBundle.message("label.do.you.want.to.execute.command.in.terminal"), command, project)

    // TODO pass from http request later (MCP Client name or something else)
    val id = "mcp_session"
    val window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
    return executeShellCommand(window = window,
                               project = project,
                               command = command,
                               executeInShell = executeInShell,
                               sessionId = if (reuseExistingTerminalWindow) id else null,
                               timeout = Constants.LONG_TIMEOUT_MILLISECONDS_VALUE.milliseconds,
                               maxLinesCount = maxLinesCount,
                               truncateMode = truncateMode
    )
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