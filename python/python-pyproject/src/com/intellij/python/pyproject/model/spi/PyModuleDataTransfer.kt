package com.intellij.python.pyproject.model.spi

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Modules can be renamed. To update external things (like run configs), implement this EP.
 */
@ApiStatus.Internal
fun interface PyModuleDataTransfer {
  companion object {
    internal val EP = ExtensionPointName.create<PyModuleDataTransfer>("com.intellij.python.pyproject.model.moduleDataTransfer")
  }

  /**
   * Called *before* all modules in [project] are renamed
   */
  suspend fun beforeRename(project: Project): AfterRename
}


@ApiStatus.Internal
fun interface AfterRename {
  /**
   * Modules rename, and [oldToNewModuleNames] are new names.
   */
  suspend fun modulesRenamed(oldToNewModuleNames: Map<String, String>)
}

