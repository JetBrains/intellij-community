@file:Suppress("FunctionName", "unused")

package com.intellij.mcpserver.toolsets.vcs

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable

class VcsToolset : McpToolset {

  @Serializable
  class VcsRoots(val roots: Array<VcsRoot>)

  @Serializable
  class VcsRoot(
    @property:McpDescription("Path of repository relative to the project directory. Empty string means the project root")
    val pathRelativeToProject: String,
    @property:McpDescription("VCS used by this repository")
    val vcsName: String)

  @McpTool
  @McpDescription("""Retrieves the list of VCS roots in the project.
    |This is useful to detect all repositories in a multi-repository project.""")
  suspend fun get_repositories(): VcsRoots {
    val project = currentCoroutineContext().project
    val projectDirectory = project.projectDirectory
    val vcs = ProjectLevelVcsManager.getInstance(project).allVcsRoots
      .mapNotNull { VcsRoot(projectDirectory.relativizeIfPossible(it.path), it.vcs?.name ?: "<Unknown VCS>") }
    return VcsRoots(vcs.toTypedArray())
  }
}