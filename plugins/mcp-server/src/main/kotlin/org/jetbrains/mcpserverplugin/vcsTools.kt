package org.jetbrains.mcpserverplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.toNioPathOrNull
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response
import kotlin.io.path.Path

class GetVcsStatusTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_project_vcs_status"
    override val description: String = """
        Retrieves the current version control status of files in the project.
        Use this tool to get information about modified, added, deleted, and moved files in your VCS (e.g., Git).
        Returns a JSON-formatted list of changed files, where each entry contains:
        - path: The file path relative to project root
        - type: The type of change (e.g., MODIFICATION, ADDITION, DELETION, MOVED)
        Returns an empty list ([]) if no changes are detected or VCS is not configured.
        Returns error "project dir not found" if project directory cannot be determined.
        Note: Works with any VCS supported by the IDE, but is most commonly used with Git
    """

    override fun handle(project: Project, args: NoArgs): Response {
        val projectDir = project.guessProjectDir()?.toNioPathOrNull()
            ?: return Response(error = "project dir not found")

        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.allChanges

        return Response(changes.mapNotNull { change ->
            val absolutePath = change.virtualFile?.path ?: change.afterRevision?.file?.path
            val changeType = change.type

            if (absolutePath != null) {
                try {
                    val relativePath = projectDir.relativize(Path(absolutePath)).toString()
                    relativePath to changeType
                } catch (e: IllegalArgumentException) {
                    null  // Skip files outside project directory
                }
            } else {
                null
            }
        }.joinToString(",\n", prefix = "[", postfix = "]") {
            """{"path": "${it.first}", "type": "${it.second}"}"""
        })
    }
}