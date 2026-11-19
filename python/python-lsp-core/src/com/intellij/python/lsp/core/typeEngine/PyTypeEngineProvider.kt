package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.python.lsp.core.PyLspToolSettings
import org.jetbrains.annotations.ApiStatus

interface PyTypeEngineProvider {
  val pyTypeEngineType: PyTypeEngineType

  fun isSupported(project: Project): Boolean

  fun updateLspServers(project: Project)

  /** Whether the engine runs a server of its own, apart from the External Tools tool of the same package. */
  @ApiStatus.Internal
  fun runsOwnServer(project: Project): Boolean = false

  /** The editor features that the Type Engine page edits, or `null` when the engine has none. */
  @ApiStatus.Internal
  fun featureSettings(project: Project): PyLspToolSettings? = null

  companion object {
    internal val EP = ExtensionPointName.create<PyTypeEngineProvider>("com.intellij.python.lsp.typeEngineConfigurable")

    @ApiStatus.Internal
    fun forType(type: PyTypeEngineType): PyTypeEngineProvider? =
      EP.extensionsIfPointIsRegistered.firstOrNull { it.pyTypeEngineType == type }

    fun getSupportedTypes(project: Project): List<PyTypeEngineType> {
      return EP.extensionsIfPointIsRegistered.mapNotNull {
        if (!it.isSupported(project)) return@mapNotNull null
        return@mapNotNull it.pyTypeEngineType
      }
    }

    fun updateLspServers(project: Project) {
      EP.extensionsIfPointIsRegistered.forEach {
        it.updateLspServers(project)
      }
    }
  }
}
