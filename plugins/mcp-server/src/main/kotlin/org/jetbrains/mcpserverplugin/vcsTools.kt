package org.jetbrains.mcpserverplugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import org.jetbrains.ide.mcp.NoArgs
import org.jetbrains.ide.mcp.Response

class GetVcsStatusTool : AbstractMcpTool<NoArgs>() {
    override val name: String = "get_project_vcs_status"
    override val description: String = "Get the status of the current VCS (Version Control System, most probably it's Git) branch of opened project"

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