package com.intellij.python.pyproject.model.spi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.PyProjectIssue
import com.intellij.python.pyproject.PyProjectTable
import com.intellij.python.pyproject.dependencies.spi.PyDependencyGroupLocator
import com.intellij.python.pyproject.psi.spi.PyProjectTomlPathLocator
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.flavor
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable

/**
 * Manager provides various specific extensions to `pyproject.toml` (i.e.: uv-specific) and coupled with the flavor of a Python interpreter.
 * [forPythonInterpreter] finds the one of an interpreter.
 */
interface PyProjectManager : PyProjectCreator, PyDependencyGroupLocator, PyProjectTomlPathLocator {
  companion object {
    /**
     * The registered managers, in load order. `DefaultPyProjectManager` is always the last element,
     * because `intellij.python.pyproject.xml` registers it with `order="last"`.
     */
    internal val EP = ExtensionPointName.create<PyProjectManager>("com.intellij.python.pyproject.model.pyprojectmanager")

    /** The manager of the tool that set up [interpreter], or `DefaultPyProjectManager` when no tool did. */
    fun forPythonInterpreter(interpreter: PythonInterpreter): PyProjectManager {
      val flavor = interpreter.flavor
      return EP.extensionList.firstOrNull { it.flavorDataType.isInstance(flavor) }
             ?: error("No PyProjectManager accepts the flavor $flavor. DefaultPyProjectManager must have order=\"last\".")
    }
  }

  /**
   * To be used by [forPythonInterpreter]
   */
  val flavorDataType: Class<out PythonSdkFlavor<*>>

  /**
   * CLI adapter for `pyproject.toml` dependency groups (PEP 735 / PEP 621), or `null` when this
   * project manager does not model group-scoped installs. `null` is the "no group support" signal
   * for install call sites — see [PySdkDependencyGroupSupport] and
   * `com.jetbrains.python.packaging.management.formatDependencyGroupArgs`.
   */
  val dependencyGroupSupport: PySdkDependencyGroupSupport?
    get() = null

  val id: ToolId
  val ui: PyToolUIInfo

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
   * Tool that supports build systems might return additional src directories.
   *
   * [toml] is the `pyproject.toml` content, [projectRoot] is the directory that holds it.
   * Report the directories the build backend declares as source roots, and use [resolveSrcRoots]
   * to turn the toml paths into them. A non-empty answer also makes this tool a participant of the project,
   * so answer only for a table this tool owns.
   */
  suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory>

  /**
   * Tool-specific toml sections where dependencies may be specified
   */
  fun getTomlDependencySpecifications(): List<TomlDependencySpecification>

  /**
   * [PyProjectTable] usually represents `[project]` table, but when it doesn't exist, this method is called as a fallback.
   * @param pyProjectToml is `pyproject.toml` content
   * @param fallbackName is a project name for virtual projects (e.g. directory name)
   * @param issues is a sink to report issues from [PyProjectTable] creation.
   */
  fun getAlternativeProjectTable(pyProjectToml: TomlTable, fallbackName: String, issues: MutableList<PyProjectIssue>): PyProjectTable? =
    null
}
