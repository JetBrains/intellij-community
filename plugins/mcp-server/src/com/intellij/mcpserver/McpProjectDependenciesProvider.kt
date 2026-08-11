package com.intellij.mcpserver

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface McpProjectDependenciesProvider {
  companion object {
    val EP: ExtensionPointName<McpProjectDependenciesProvider> = ExtensionPointName
      .create("com.intellij.mcpServer.projectDependenciesProvider")
  }

  suspend fun collectDependencies(project: Project): List<McpProjectDependency>
}

@ApiStatus.Experimental
data class McpProjectDependency(
  @JvmField val name: String,
  @JvmField val version: String? = null,
  @JvmField val dependencyType: String? = null,
  @JvmField val source: String? = null,
)
