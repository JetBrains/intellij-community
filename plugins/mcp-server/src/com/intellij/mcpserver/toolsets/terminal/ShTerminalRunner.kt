package com.intellij.mcpserver.toolsets.terminal

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalWidget
import com.intellij.terminal.ui.TerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.jediterm.terminal.TtyConnector
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalUtil
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager

object ShTerminalRunner {
    fun run(
        project: Project,
        command: String,
        workingDirectory: String,
        @NlsContexts.TabTitle title: String,
        activateToolWindow: Boolean
    ): TerminalWidget? {
        val terminalToolWindowManager = TerminalToolWindowManager.getInstance(project)
        val window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
            ?: return null

        val contentManager = window.contentManager
        val pair = getSuitableProcess(project, contentManager, workingDirectory)

        if (pair == null) {
            val widget = terminalToolWindowManager.createShellWidget(
                workingDirectory,
                title,
                activateToolWindow,
                activateToolWindow
            )
            widget.sendCommandToExecute(command)

            return widget
        }

        if (activateToolWindow) {
            window.activate(null)
        }

        pair.first.displayName = title
        contentManager.setSelectedContent(pair.first)
        pair.second.sendCommandToExecute(command)
        return pair.second
    }

    fun isAvailable(project: Project): Boolean {
        val window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
        return window != null && window.isAvailable
    }

    private fun getSuitableProcess(
        project: Project,
        contentManager: ContentManager,
        workingDirectory: String
    ): Pair<Content, TerminalWidget>? {
        val selectedContent = contentManager.selectedContent
        if (selectedContent != null) {
            getSuitableProcess(project, selectedContent, workingDirectory)?.let { return it }
        }

        return contentManager.contents
            .asSequence()
            .mapNotNull { content -> getSuitableProcess(project, content, workingDirectory) }
            .firstOrNull()
    }

    fun getSuitableProcess(
        project: Project,
        content: Content,
        workingDirectory: String
    ): Pair<Content, TerminalWidget>? {
        val widget = TerminalToolWindowManager.findWidgetByContent(content) ?: return null

        if (widget is JBTerminalWidget && widget !is ShellTerminalWidget) {
            return null
        }

        if (widget is ShellTerminalWidget && widget.typedShellCommand.isNotEmpty()) {
            return null
        }

        val processTtyConnector = ShellTerminalWidget.getProcessTtyConnector(widget.ttyConnector) ?: return null
        if (TerminalUtil.hasRunningCommands(processTtyConnector as TtyConnector)) {
            return null
        }

        val currentWorkingDirectory = TerminalWorkingDirectoryManager.getWorkingDirectory(widget)
        if (!FileUtil.pathsEqual(workingDirectory, currentWorkingDirectory)) {
            return null
        }

        return Pair(content, widget)
    }
}