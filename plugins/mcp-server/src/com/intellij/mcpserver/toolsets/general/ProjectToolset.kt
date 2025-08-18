@file:Suppress("FunctionName", "unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.*
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

class ProjectToolset : McpToolset {

  @McpTool
  @McpDescription("""
    |Returns a list of all open projects in the IDE.
    |Use this tool to discover available projects and their basic information.
    |This is useful when you need to work with multiple projects and want to specify which project to operate on.
  """)
  suspend fun list_projects(): ProjectsList {
    currentCoroutineContext().reportToolActivity(McpServerBundle.message("tool.activity.listing.projects"))
    
    val projects = ProjectManager.getInstance().openProjects.map { project ->
      ProjectInfo(
        name = project.name,
        basePath = project.basePath ?: "",
        isDefault = project.isDefault
      )
    }
    
    return ProjectsList(projects)
  }

  @Serializable
  data class ProjectsList(
    val projects: List<ProjectInfo>
  )

  @Serializable
  data class ProjectInfo(
    val name: String,
    val basePath: String,
    @EncodeDefault(mode = EncodeDefault.Mode.NEVER)
    val isDefault: Boolean? = null
  )
}
