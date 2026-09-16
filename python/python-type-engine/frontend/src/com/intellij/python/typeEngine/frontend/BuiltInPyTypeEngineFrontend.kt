// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine.frontend

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowsRange

internal class BuiltInPyTypeEngineFrontend : PyTypeEngineFrontend {
  override val id: PyTypeEngineId = PyTypeEngineId.PYCHARM
  override val presentableName: String get() = TypeEngineFrontendBundle.message("engine.built.in.name")

  override fun Panel.createConfigurableContent(project: Project, propertyGraph: PropertyGraph): RowsRange = rowsRange {
    row {
      comment(TypeEngineFrontendBundle.message("pycharm.description"))
    }
  }
}
