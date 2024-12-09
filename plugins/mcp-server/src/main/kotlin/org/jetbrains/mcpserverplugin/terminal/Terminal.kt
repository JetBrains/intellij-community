package org.jetbrains.mcpserverplugin.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.ide.mcp.McpTool
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalView
import javax.swing.JComponent
import java.lang.Thread
import kotlin.reflect.KClass

class GetTerminalTextTool : McpTool<NoArgs> {
    override val name: String = "get_terminal_text"
    override val description: String = "Get the current contents of a terminal in JetBrains IDE"
    override val argKlass: KClass<NoArgs> = NoArgs::class

    override fun handle(project: Project, args: NoArgs): Response {
        // Retrieve the first terminal widget text
        val text = com.intellij.openapi.application.runReadAction<String?> {
            TerminalView.getInstance(project).getWidgets().firstOrNull()?.text
        }
        return Response(text)
    }
}

data class ExecuteTerminalCommandArgs(val command: String)
class ExecuteTerminalCommandTool : McpTool<ExecuteTerminalCommandArgs> {
    override val name: String = "execute_terminal_command"
    override val description: String = "Execute any terminal command in JetBrains IDE"
    override val argKlass: KClass<ExecuteTerminalCommandArgs> = ExecuteTerminalCommandArgs::class

    override fun handle(project: Project, args: ExecuteTerminalCommandArgs): Response {
        var result = Response(error = "canceled")

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

            if (confirmationDialog.isOK) {
                val terminalWidget = terminalWidget(project)
                terminalWidget?.sendCommandToExecute("clear; " + args.command)

                val startTime = System.currentTimeMillis()
                while (terminalWidget?.hasRunningCommands() == true &&
                    System.currentTimeMillis() - startTime < 1000
                ) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        result = Response(error = "Execution interrupted")
                        return@invokeAndWait
                    }
                }

                val terminalOutput = TerminalView.getInstance(project).getWidgets().firstOrNull()?.text ?: "No output collected."
                result = Response(terminalOutput)
            }
        }

        return result
    }

    private fun terminalWidget(project: Project): TerminalWidget? {
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        return terminalManager.terminalWidgets.firstOrNull() ?: terminalManager.createNewSession()
    }
}

private fun TerminalWidget?.hasRunningCommands(): Boolean {
    if (this == null) return false

    try {
        val bridgeClass = Class.forName("com.intellij.terminal.JBTerminalWidget\$TerminalWidgetBridge")
        val widgetMethod = bridgeClass.getDeclaredMethod("widget")
        widgetMethod.isAccessible = true

        val shellWidget = widgetMethod.invoke(this)
        if (shellWidget is ShellTerminalWidget) {
            return shellWidget.hasRunningCommands()
        }
    } catch (e: Exception) {
        // Handle or log the exception
        return false
    }
    return false
}
