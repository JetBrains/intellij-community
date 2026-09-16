// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ty.frontend

import com.intellij.openapi.components.service
import com.intellij.openapi.components.Service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.project.projectId
import com.intellij.python.typeEngine.common.PyTypeEngineApi
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.python.typeEngine.common.PyTypeEngineOperationResultDto
import com.intellij.python.typeEngine.common.PyTypeEngineRequest
import com.intellij.python.typeEngine.frontend.PyTypeEngineFrontend
import com.intellij.python.typeEngine.frontend.PyTypeEngineFrontendState
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowsRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class TyTypeEngineFrontend : PyTypeEngineFrontend {
  override val id: PyTypeEngineId = PyTypeEngineId.TY
  override val presentableName: String = "ty"

  @Suppress("DialogTitleCapitalization")
  override fun Panel.createConfigurableContent(project: Project, propertyGraph: PropertyGraph): RowsRange = rowsRange {
    val installed = propertyGraph.property(id in PyTypeEngineFrontendState.getInstance(project).get().installed)

    row {
      comment(TyFrontendBundle.message("ty.type.engine.description"))
    }

    row {
      button(TyFrontendBundle.message("install.ty.button")) {
        project.service<TyTypeEngineFrontendService>().scope.launch {
          withBackgroundProgress(project, TyFrontendBundle.message("install.ty.progress"), true) {
            when (val result = PyTypeEngineApi.getInstance().install(PyTypeEngineRequest(project.projectId(), id))) {
              is PyTypeEngineOperationResultDto.Success -> {
                PyTypeEngineFrontendState.getInstance(project).apply(result.state)
                installed.set(true)
                Messages.showInfoMessage(
                  project,
                  TyFrontendBundle.message("install.ty.success", result.path.orEmpty()),
                  TyFrontendBundle.message("install.ty.title"),
                )
              }
              is PyTypeEngineOperationResultDto.Failure -> Messages.showErrorDialog(
                project,
                TyFrontendBundle.message("install.ty.failed"),
                TyFrontendBundle.message("install.ty.title"),
              )
            }
          }
        }
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class TyTypeEngineFrontendService(val scope: CoroutineScope)
