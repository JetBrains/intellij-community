package org.jetbrains.mcpserverplugin.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.dsl.builder.panel
import kotlinx.serialization.Serializable
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalView
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JComponent

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
        // Retrieve the first terminal widget text
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
        - Limits output to 200 lines (truncates excess with a notification)
        Returns one of these possible responses:
        - The terminal output if command executes successfully
        - "No output collected" if execution succeeds but no output is captured
        - Error messages:
          * "canceled" if user denies confirmation
          * "Command execution timed out after 5 seconds" if execution exceeds timeout
          * "Execution error: [details]" for other failures
    """

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

            val terminalWidget = ShTerminalRunner.run(project, "clear; " + args.command, project.basePath ?: "", "MCP Command", true)

            val jediTermWidget = if (terminalWidget != null) ShellTerminalWidget.asShellJediTermWidget(terminalWidget) else null

            if (jediTermWidget == null) {
                future.complete(Response(error = "No terminal available"))
                return@invokeAndWait
            }

            // Schedule a single delayed task to collect output
            ApplicationManager.getApplication().executeOnPooledThread {
                Thread.sleep(100) // Wait 100ms

                val terminalOutput = jediTermWidget.text

                // Limit to 200 lines if needed
                val lines = terminalOutput.lines()
                val finalOutput = if (lines.size > 200) {
                    lines.take(200).joinToString("\n") + "\n... (output truncated at 200 lines)"
                } else {
                    terminalOutput
                }

                future.complete(Response(finalOutput))
            }
        }

        try {
            return future.get(5, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            return Response(error = "Command execution timed out after 5 seconds")
        } catch (e: Exception) {
            return Response(error = "Execution error: ${e.message}")
        }
    }
}