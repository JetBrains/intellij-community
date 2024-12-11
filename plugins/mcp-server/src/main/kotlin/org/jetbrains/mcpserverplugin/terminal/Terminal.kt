package org.jetbrains.mcpserverplugin.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import com.jediterm.terminal.TtyConnector
import kotlinx.serialization.Serializable
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.TerminalView
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JComponent

val maxLineCount = 2000
val timeout = TimeUnit.MINUTES.toMillis(2)

class GetTerminalTextTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_terminal_text"
    override val description: String = """
        Retrieves the current text content from the first active terminal in the IDE.
        Use this tool to access the terminal's output and command history.
        Returns one of two possible responses:
        - The terminal's text content if a terminal exists
        - empty string if no terminal is open or available
        Note: Only captures text from the first terminal if multiple terminals are open
    """

    override fun handle(project: Project, args: NoArgs): Response {
        val text = com.intellij.openapi.application.runReadAction<String?> {
            TerminalView.getInstance(project).getWidgets().firstOrNull()?.text
        }
        return Response(text ?: "")
    }
}

@Serializable
data class ExecuteTerminalCommandArgs(val command: String)

class ExecuteTerminalCommandTool : AbstractMcpTool<ExecuteTerminalCommandArgs>() {
    override val name: String = "execute_terminal_command"
    override val description: String = """
        Executes a specified shell command in the IDE's integrated terminal.
        Use this tool to run terminal commands within the IDE environment.
        Requires a command parameter containing the shell command to execute.
        Important features and limitations:
        - Checks if process is running before collecting output
        - Limits output to $maxLineCount lines (truncates excess)
        - Times out after $timeout milliseconds with notification
        Returns possible responses:
        - Terminal output (truncated if >$maxLineCount lines)
        - Output with interruption notice if timed out
        - Error messages for various failure cases
    """

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
            lines.take(maxLineCount).joinToString("\n") + "\n... (output truncated at 200 lines)"
        } else {
            output
        }
    }

    override fun handle(project: Project, args: ExecuteTerminalCommandArgs): Response {
        val future = CompletableFuture<Response>()

        ApplicationManager.getApplication().invokeAndWait {
            val confirmationDialog = object : DialogWrapper(project, true) {
                init {
                    init()
                    title = "Confirm Command Execution"
                }

                override fun createCenterPanel(): JComponent? {
                    return panel {
                        row {
                            label("Do you want to run command `${args.command.take(100)}` in the terminal?")
                        }
                    }
                }
            }
            confirmationDialog.show()

            if (!confirmationDialog.isOK) {
                future.complete(Response(error = "canceled"))
                return@invokeAndWait
            }

            val terminalWidget =
                ShTerminalRunner.run(project, "clear; " + args.command, project.basePath ?: "", "MCP Command", true)
            val shellWidget =
                if (terminalWidget != null) ShellTerminalWidget.asShellJediTermWidget(terminalWidget) else null

            if (shellWidget == null) {
                future.complete(Response(error = "No terminal available"))
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

                future.complete(Response(finalOutput))
            }
        }

        try {
            return future.get(
                timeout + 2000,
                TimeUnit.MILLISECONDS
            ) // Give slightly more time than the internal timeout
        } catch (e: TimeoutException) {
            return Response(error = "Command execution timed out after $timeout milliseconds")
        } catch (e: Exception) {
            return Response(error = "Execution error: ${e.message}")
        }
    }
}