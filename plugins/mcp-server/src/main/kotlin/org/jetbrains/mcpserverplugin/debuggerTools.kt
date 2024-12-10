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

@Serializable
data class ToggleBreakpointArgs(val filePathInProject: String, val line: Int)
class ToggleBreakpointTool : AbstractMcpTool<ToggleBreakpointArgs>() {
    override val name: String = "toggle_debugger_breakpoint"
    override val description: String = "Toggle debugger breakpoint at specified location"

    override fun handle(
        project: Project,
        args: ToggleBreakpointArgs
    ): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response("can't find project dir")
        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(projectDir.resolve(args.filePathInProject))

        runWriteAction {
            val position = XSourcePositionImpl.create(virtualFile, args.line)
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
    override val description: String = "Get list of all debugger breakpoints in the project"

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