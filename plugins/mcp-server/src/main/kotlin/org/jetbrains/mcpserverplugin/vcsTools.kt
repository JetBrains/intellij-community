package org.jetbrains.mcpserverplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response

class GetVcsStatusTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_project_vcs_status"
    override val description: String = """
        Retrieves the current version control status of files in the project.
        Use this tool to get information about modified, added, deleted, and moved files in your VCS (e.g., Git).
        Returns a JSON-formatted list of changed files, where each entry contains:
        - path: The absolute path of the changed file
        - type: The type of change (e.g., MODIFICATION, ADDITION, DELETION, MOVED)
        Returns an empty list ([]) if no changes are detected or VCS is not configured.
        Note: Works with any VCS supported by the IDE, but is most commonly used with Git
    """

    override fun handle(project: Project, args: NoArgs): Response {
        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.allChanges

        return Response(changes.mapNotNull { change ->
            val filePath = change.virtualFile?.path ?: change.afterRevision?.file?.path
            val changeType = change.type
            if (filePath != null) {
                filePath to changeType
            } else {
                null
            }
        }.joinToString(",\n", prefix = "[", postfix = "]") {
            """{"path": "${it.first}", "type": "${it.second}"}"""
        })
    }
}