// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.projectId
import com.intellij.python.typeEngine.common.PyTypeEngineApi
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.python.typeEngine.common.PyTypeEngineStateDto
import fleet.rpc.client.durable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.PROJECT)
class PyTypeEngineFrontendState(project: Project, scope: CoroutineScope) {
  private val state = MutableStateFlow(
    PyTypeEngineStateDto(
      selected = PyTypeEngineId.PYCHARM,
      supported = setOf(PyTypeEngineId.PYCHARM),
      installed = setOf(PyTypeEngineId.PYCHARM),
    )
  )
  private val initialized = CompletableDeferred<Unit>()

  init {
    scope.launch {
      durable {
        PyTypeEngineApi.getInstance().observeState(project.projectId()).collect { newState ->
          state.value = newState
          initialized.complete(Unit)
        }
      }
    }
  }

  fun get(): PyTypeEngineStateDto = state.value

  fun states(): StateFlow<PyTypeEngineStateDto> = state.asStateFlow()

  fun apply(newState: PyTypeEngineStateDto) {
    state.value = newState
  }

  suspend fun awaitInitialized() {
    initialized.await()
  }

  companion object {
    fun getInstance(project: Project): PyTypeEngineFrontendState = project.service()
  }
}

internal class PyTypeEngineFrontendStateStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    PyTypeEngineFrontendState.getInstance(project).awaitInitialized()
  }
}
