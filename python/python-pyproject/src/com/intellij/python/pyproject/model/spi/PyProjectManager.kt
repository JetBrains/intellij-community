package com.intellij.python.pyproject.model.spi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.pyproject.dependencies.spi.PyDependencyGroupLocator
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.venvReader.Directory
import org.apache.tuweni.toml.TomlTable

/**
 * Manager provides various specific extensions to `pyproject.toml` (i.e.: uv-specific) and coupled with python SDK with additional data.
 * It can also be created by [forSdk].
 */
interface PyProjectManager : PyDependencyGroupLocator {
  companion object {
    internal val EP = ExtensionPointName.create<PyProjectManager>("com.intellij.python.pyproject.model.pyprojectmanager")

    fun forSdk(sdk: Sdk): PyProjectManager? {
      val additionalData = sdk.pySdkAdditionalData
      return EP.extensionList.firstOrNull { it.additionalDataType.isInstance(additionalData) }
    }
  }

  /**
   * To be used by [forSdk]
   */
  val additionalDataType: Class<out PythonSdkAdditionalData>

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
   * Tool that supports build systems might return additional src directories
   */
  suspend fun getSrcRoots(toml: TomlTable, projectRoot: Directory): Set<Directory>

  /**
   * Tool-specific toml sections where dependencies may be specified
   */
  fun getTomlDependencySpecifications(): List<TomlDependencySpecification>
}
