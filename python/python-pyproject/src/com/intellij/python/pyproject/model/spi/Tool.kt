package com.intellij.python.pyproject.model.spi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.common.tools.ToolId
import com.intellij.python.pyproject.model.internal.pyProjectToml.TomlDependencySpecification
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable

interface Tool {
  companion object {
    internal val EP = ExtensionPointName.create<Tool>("com.intellij.python.pyproject.model.tool")
  }

  val id: ToolId
  val ui: PyToolUIInfo?

  /**
   * Tools that support old (tool-specific) naming should return it here
   */
  suspend fun getProjectName(projectToml: TomlTable): @NlsSafe String?

  /**
   * Tool uses [entries] ([rootIndex] contains the same data, used as index rooDir->project name) to report project dependencies and workspace members.
   * All project names must be taken from provided data (use [rootIndex] to get name by directory).
   * If tool doesn't provide any specific structure (i.e: no dependencies except those described in pyproject.toml spec, no workspaces) return `null`
   */
  suspend fun getProjectStructure(
    entries: Map<ProjectName, PyProjectTomlProject>,
    rootIndex: Map<Directory, ProjectName>,
  ): ProjectStructureInfo?

  /**
   * Tool that supports build systems might return additional src directories
   */
  suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory>

  /**
   * Tool-specific toml sections where dependencies may be specified
   */
  fun getTomlDependencySpecifications(): List<TomlDependencySpecification>
}
