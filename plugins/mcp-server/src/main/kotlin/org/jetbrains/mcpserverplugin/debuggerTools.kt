package org.jetbrains.mcpserverplugin

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import kotlinx.serialization.Serializable
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import org.jetbrains.mcpserverplugin.general.resolveRel

@Serializable
data class ToggleBreakpointArgs(val filePathInProject: String, val line: Int)
class ToggleBreakpointTool : AbstractMcpTool<ToggleBreakpointArgs>() {
    override val name: String = "toggle_debugger_breakpoint"
    override val description: String = """
        Toggles a debugger breakpoint at the specified line in a project file.
        Use this tool to add or remove breakpoints programmatically.
        Requires two parameters:
        - filePathInProject: The relative path to the file within the project
        - line: The line number where to toggle the breakpoint. The line number is starts at 1 for the first line.
        Returns one of two possible responses:
        - "ok" if the breakpoint was successfully toggled
        - "can't find project dir" if the project directory cannot be determined
        Note: Automatically navigates to the breakpoint location in the editor
    """

    override fun handle(
        project: Project,
        args: ToggleBreakpointArgs
    ): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "can't find project dir")
        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(projectDir.resolveRel(args.filePathInProject))

        runWriteAction {
            val position = XSourcePositionImpl.create(virtualFile, args.line - 1)
            XBreakpointUtil.toggleLineBreakpoint(project, position, false, null, false, true, true).onSuccess {
                 invokeLater {
                     position.createNavigatable(project).navigate(true)
                 }
            }
        }

        return Response("ok")
    }
}

class GetBreakpointsTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_debugger_breakpoints"
    override val description: String = """
        Retrieves a list of all line breakpoints currently set in the project.
        Use this tool to get information about existing debugger breakpoints.
        Returns a JSON-formatted list of breakpoints, where each entry contains:
        - path: The absolute file path where the breakpoint is set
        - line: The line number (1-based) where the breakpoint is located
        Returns an empty list ([]) if no breakpoints are set.
        Note: Only includes line breakpoints, not other breakpoint types (e.g., method breakpoints)
    """

    override fun handle(
        project: Project,
        args: NoArgs
    ): Response {
        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        return Response(breakpointManager.allBreakpoints
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
                """{"path": "${it.first}", "type": "${it.second}"}"""
            })
    }
}
