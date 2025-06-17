@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.debugger

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.resolveRel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

class DebuggerToolset : McpToolset {
    @McpTool
    @McpDescription("""
        Toggles a debugger breakpoint at the specified line in a project file.
        Use this tool to add or remove breakpoints programmatically.
        Requires two parameters:
        - filePathInProject: The relative path to the file within the project
        - line: The line number where to toggle the breakpoint. The line number is starts at 1 for the first line.
        Returns one of two possible responses:
        - "ok" if the breakpoint was successfully toggled
        - "can't find project dir" if the project directory cannot be determined
        Note: Automatically navigates to the breakpoint location in the editor
    """)
    suspend fun toggle_debugger_breakpoint(
        @McpDescription("Relative path to the file within the project")
        filePathInProject: String,
        @McpDescription("Line number where to toggle the breakpoint (1-based)")
        line: Int
    ): String {
        val project = currentCoroutineContext().project
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return "can't find project dir"
        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(projectDir.resolveRel(filePathInProject))
            ?: return "can't find file '$filePathInProject'"

        val editors = withContext(Dispatchers.EDT) { FileEditorManager.getInstance (project).openFile(virtualFile, true) }
        val editor = editors.filterIsInstance<TextEditor>().firstOrNull()?.editor
                     ?: return "can't find editor for file '$filePathInProject'"
      writeAction {
          val position = XSourcePositionImpl.create(virtualFile, line - 1)
          XBreakpointUtil.toggleLineBreakpoint(project, position, false, editor, false, true, true).onSuccess {
               invokeLater {
                   position.createNavigatable(project).navigate(true)
               }
          }
      }

        return "ok"
    }

    @McpTool
    @McpDescription("""
        Retrieves a list of all line breakpoints currently set in the project.
        Use this tool to get information about existing debugger breakpoints.
        Returns a JSON-formatted list of breakpoints, where each entry contains:
        - path: The absolute file path where the breakpoint is set
        - line: The line number (1-based) where the breakpoint is located
        Returns an empty list ([]) if no breakpoints are set.
        Note: Only includes line breakpoints, not other breakpoint types (e.g., method breakpoints)
    """)
    suspend fun get_debugger_breakpoints(): String {
        val project = currentCoroutineContext().project
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        return breakpointManager.allBreakpoints
            .filterIsInstance<XLineBreakpoint<*>>() // Only consider line breakpoints
            .mapNotNull { breakpoint ->
                val filePath = breakpoint.presentableFilePath
                val line = breakpoint.line
                if (filePath != null && line >= 0) {
                    filePath to (line + 1) // Convert line from 0-based to 1-based
                } else {
                    null
                }
            }.joinToString(",\n", prefix = "[", postfix = "]") {
                """{"path": "${it.first}", "line": ${it.second}}"""
            }
    }
}