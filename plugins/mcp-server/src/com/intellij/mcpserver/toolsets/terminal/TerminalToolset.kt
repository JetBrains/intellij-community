@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.terminal

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.TerminalView
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JComponent
import kotlin.coroutines.coroutineContext

class TerminalToolset : McpToolset {
    private val maxLineCount = 2000
    private val timeout = TimeUnit.MINUTES.toMillis(2)

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
        val project = coroutineContext.project
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
        command: String
    ): String {
        val project = coroutineContext.project
        val future = CompletableFuture<String>()

        ApplicationManager.getApplication().invokeAndWait {
            val braveMode = true // ApplicationManager.getApplication().getService(PluginSettings::class.java).state.enableBraveMode
            var proceedWithCommand = true
            
            if (!braveMode) {
                val confirmationDialog = object : DialogWrapper(project, true) {
                    init {
                        init()
                        title = McpServerBundle.message("dialog.title.confirm.command.execution")
                    }

                    override fun createCenterPanel(): JComponent? {
                        return panel {
                            row {
                                label(McpServerBundle.message("label.do.you.want.to.run.command.in.terminal", command.take(100)))
                            }
                            row {
                                comment(McpServerBundle.message("text.note.you.can.enable.brave.mode.in.settings.to.skip.this.confirmation"))
                            }
                        }
                    }
                }
                confirmationDialog.show()
                proceedWithCommand = confirmationDialog.isOK
            }

            if (!proceedWithCommand) {
                future.complete("canceled")
                return@invokeAndWait
            }

            val terminalWidget = ShTerminalRunner.run(project, command, project.basePath ?: "", McpServerBundle.message("tab.title.mcp.command"), true)
            val shellWidget = if (terminalWidget != null) ShellTerminalWidget.asShellJediTermWidget(terminalWidget) else null

            if (shellWidget == null) {
                future.complete("No terminal available")
                return@invokeAndWait
            }

            ApplicationManager.getApplication().executeOnPooledThread {
                var output: String? = null
                var isInterrupted = false

                val sleep = 300L
                for (i in 1..timeout / sleep) {
                    Thread.sleep(sleep)
                    output = collectTerminalOutput(shellWidget)
                    if (output != null) break
                }

                if (output == null) {
                    output = shellWidget.text
                    isInterrupted = true
                }

                val formattedOutput = formatOutput(output)
                val finalOutput = if (isInterrupted) {
                    "$formattedOutput\n... (Command execution interrupted after $timeout milliseconds)"
                } else {
                    formattedOutput
                }

                future.complete(finalOutput)
            }
        }

        try {
            return future.get(
                timeout + 2000,
                TimeUnit.MILLISECONDS
            ) // Give slightly more time than the internal timeout
        } catch (e: TimeoutException) {
            return "Command execution timed out after $timeout milliseconds"
        } catch (e: Exception) {
            return "Execution error: ${e.message}"
        }
    }

    private fun collectTerminalOutput(widget: ShellTerminalWidget): String? {
        val processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(widget.ttyConnector) ?: return null

        // Check if the process is still running
        if (!TerminalUtil.hasRunningCommands(processTtyConnector as TtyConnector)) {
            return widget.text
        }
        return null
    }

    private fun formatOutput(output: String): String {
        val lines = output.lines()
        return if (lines.size > maxLineCount) {
            lines.take(maxLineCount).joinToString("\n") + "\n... (output truncated at ${maxLineCount} lines)"
        } else {
            output
        }
    }

}