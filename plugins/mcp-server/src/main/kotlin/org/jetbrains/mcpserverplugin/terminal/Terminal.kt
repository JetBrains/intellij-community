package org.jetbrains.mcpserverplugin.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.ide.mcp.McpTool
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalView
import javax.swing.JComponent
import kotlin.reflect.KClass

data class GetTerminalTextResult(val text: String?)

class GetTerminalTextTool : McpTool<NoArgs, GetTerminalTextResult> {
    override val name: String = "get_terminal_text"
    override val description: String = "Get the current contents of a terminal in JetBrains IDE"
    override val argKlass: KClass<NoArgs> = NoArgs::class

    override fun handle(project: Project, args: NoArgs): GetTerminalTextResult {
        // Retrieve the first terminal widget text
        val text = com.intellij.openapi.application.runReadAction<String?> {
            TerminalView.getInstance(project).getWidgets().firstOrNull()?.text
        }
        return GetTerminalTextResult(text)
    }
}

data class ExecuteTerminalCommandArgs(val command: String)
data class ExecuteTerminalCommandResult(val status: String)

class ExecuteTerminalCommandTool : McpTool<ExecuteTerminalCommandArgs, ExecuteTerminalCommandResult> {
    override val name: String = "execute_terminal_command"
    override val description: String = "Execute any terminal command in JetBrains IDE"
    override val argKlass: KClass<ExecuteTerminalCommandArgs> = ExecuteTerminalCommandArgs::class

    override fun handle(project: Project, args: ExecuteTerminalCommandArgs): ExecuteTerminalCommandResult {
        var result = ExecuteTerminalCommandResult("canceled")

        ApplicationManager.getApplication().invokeAndWait {
            val confirmationDialog = object : DialogWrapper(project, true) {
                init {
                    init()
                    title = "Confirm Command Execution"
                }

                override fun createCenterPanel(): JComponent? {
                    return panel {
                        row {
                            label("Do you want to run command `${args.command}` in the terminal?")
                        }
                    }
                }
            }
            confirmationDialog.show()

            if (confirmationDialog.isOK) {
                terminalWidget(project)?.sendCommandToExecute(args.command)
                result = ExecuteTerminalCommandResult("ok")
            }
        }

        return result
    }

    private fun terminalWidget(project: Project): TerminalWidget? {
        return TerminalToolWindowManager.getInstance(project).terminalWidgets.firstOrNull()
    }
}