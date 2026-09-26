package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.project.Project

internal class BuiltInPyTypeEngineProvider : PyTypeEngineProvider {
  override val pyTypeEngineType: PyTypeEngineType
    get() = PyTypeEngineType.PYCHARM

  override fun updateLspServers(project: Project) {}

  override fun isSupported(project: Project): Boolean = true
}
