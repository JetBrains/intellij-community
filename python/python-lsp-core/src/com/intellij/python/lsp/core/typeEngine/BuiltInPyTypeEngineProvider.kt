package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.python.lsp.core.PyLspCoreBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowsRange

internal class BuiltInPyTypeEngineProvider : PyTypeEngineProvider {
  override val pyTypeEngineType: PyTypeEngineType
    get() = PyTypeEngineType.PYCHARM

  override fun updateLspServers(project: Project) {}

  override fun isSupported(project: Project): Boolean = true

  override fun Panel.createConfigurableContent(project: Project, propertyGraph: PropertyGraph): RowsRange {
    return rowsRange {
      row {
        comment(PyLspCoreBundle.message("pycharm.description"))
      }
    }
  }
}
