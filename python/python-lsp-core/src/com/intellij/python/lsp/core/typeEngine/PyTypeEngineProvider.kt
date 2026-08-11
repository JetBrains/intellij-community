package com.intellij.python.lsp.core.typeEngine

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowsRange

interface PyTypeEngineProvider {
  val pyTypeEngineType: PyTypeEngineType

  fun isSupported(project: Project): Boolean

  fun updateLspServers(project: Project)

  fun Panel.createConfigurableContent(project: Project, propertyGraph: PropertyGraph): RowsRange

  companion object {
    internal val EP = ExtensionPointName.create<PyTypeEngineProvider>("com.intellij.python.lsp.typeEngineConfigurable")

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
