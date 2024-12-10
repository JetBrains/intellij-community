package org.jetbrains.mcpserverplugin.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.dsl.builder.panel
import kotlinx.serialization.Serializable
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.AbstractMcpTool
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalView
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JComponent

@Serializable
data class ExecuteTerminalCommandArgs(val command: String)

class ExecuteTerminalCommandTool : AbstractMcpTool<ExecuteTerminalCommandArgs>() {
    override val name: String = "execute_terminal_command"
    override val description: String = "Execute any terminal command in JetBrains IDE"

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

            val terminalWidget = terminalWidget(project)
            terminalWidget?.sendCommandToExecute("clear; " + args.command)

            // Schedule a single delayed task to collect output
            ApplicationManager.getApplication().executeOnPooledThread {
                Thread.sleep(100) // Wait 100ms
                val terminalOutput = TerminalView.getInstance(project)
                    .getWidgets()
                    .firstOrNull()?.text ?: "No output collected."

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

    private fun terminalWidget(project: Project): TerminalWidget? {
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        return terminalManager.terminalWidgets.firstOrNull() ?: terminalManager.createNewSession()
    }
}