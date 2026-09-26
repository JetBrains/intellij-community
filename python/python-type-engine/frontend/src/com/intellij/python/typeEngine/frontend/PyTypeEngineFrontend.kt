// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine.frontend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowsRange
import org.jetbrains.annotations.Nls

interface PyTypeEngineFrontend {
  val id: PyTypeEngineId
  val presentableName: @Nls String

  fun Panel.createConfigurableContent(project: Project, propertyGraph: PropertyGraph): RowsRange

  companion object {
    val EP_NAME: ExtensionPointName<PyTypeEngineFrontend> =
      ExtensionPointName.create("com.intellij.python.typeEngine.frontend")

    fun getSupported(project: Project): List<PyTypeEngineFrontend> {
      val supported = PyTypeEngineFrontendState.getInstance(project).get().supported
      return EP_NAME.extensionList.filter { it.id in supported }
    }
  }
}
