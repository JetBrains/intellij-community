// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import com.jediterm.terminal.TtyConnector
import kotlinx.serialization.Serializable
import com.intellij.mcpserver.NoArgs
import com.intellij.mcpserver.Response
import com.intellij.mcpserver.AbstractMcpTool
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.settings.PluginSettings
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.TerminalView
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JComponent

val maxLineCount = 2000
val timeout = TimeUnit.MINUTES.toMillis(2)

class GetTerminalTextTool : AbstractMcpTool<NoArgs>(NoArgs.serializer()) {
    override val name: String = "get_terminal_text"
    override val description: String = """
        Retrieves the current text content from the first active terminal in the IDE.
        Use this tool to access the terminal's output and command history.
        Returns one of two possible responses:
        - The terminal's text content if a terminal exists
        - empty string if no terminal is open or available
        Note: Only captures text from the first terminal if multiple terminals are open
    """

    override suspend fun handle(project: Project, args: NoArgs): Response {
        val text = com.intellij.openapi.application.runReadAction<String?> {
            TerminalView.getInstance(project).getWidgets().firstOrNull()?.text
        }
        return Response(text ?: "")
    }
}

@Serializable
data class ExecuteTerminalCommandArgs(val command: String)

class ExecuteTerminalCommandTool : AbstractMcpTool<ExecuteTerminalCommandArgs>(ExecuteTerminalCommandArgs.serializer()) {
    override val name: String = "execute_terminal_command"
    override val description: String = """
        Executes a specified shell command in the IDE's integrated terminal.
        Use this tool to run terminal commands within the IDE environment.
        Requires a command parameter containing the shell command to execute.
        Important features and limitations:
        - Checks if process is running before collecting output
        - Limits output to $maxLineCount lines (truncates excess)
        - Times out after $timeout milliseconds with notification
        - Requires user confirmation unless "Brave Mode" is enabled in settings
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
            lines.take(maxLineCount).joinToString("\n") + "\n... (output truncated at ${maxLineCount} lines)"
        } else {
            output
        }
    }

    override suspend fun handle(project: Project, args: ExecuteTerminalCommandArgs): Response {
        val future = CompletableFuture<Response>()

        ApplicationManager.getApplication().invokeAndWait {
            val braveMode = ApplicationManager.getApplication().getService(PluginSettings::class.java).state.enableBraveMode
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
                                label(McpServerBundle.message("label.do.you.want.to.run.command.in.terminal", args.command.take(100)))
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
                future.complete(Response(error = "canceled"))
                return@invokeAndWait
            }

            val terminalWidget =
                ShTerminalRunner.run(project, args.command, project.basePath ?: "", McpServerBundle.message("tab.title.mcp.command"), true)
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