// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ty.frontend

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.python.typeEngine.frontend.PyTypeEngineFrontend
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowsRange

internal class TyTypeEngineFrontend : PyTypeEngineFrontend {
  override val id: PyTypeEngineId = PyTypeEngineId.TY
  override val presentableName: String = "ty"

  override fun Panel.createConfigurableContent(project: Project, propertyGraph: PropertyGraph): RowsRange = rowsRange {
    row {
      comment(TyFrontendBundle.message("ty.type.engine.description"))
    }
  }
}
