// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.projectId
import com.intellij.python.pytools.common.PyToolApi
import com.intellij.python.pytools.common.PyToolEnabledStateDto
import com.intellij.python.pytools.common.PyToolId
import fleet.rpc.client.durable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** Frontend mirror of backend-authoritative tool enablement. */
@Service(Service.Level.PROJECT)
class PyToolsFrontendState(project: Project, scope: CoroutineScope) {
  private val enabled = AtomicReference<Map<PyToolId, Boolean>>(emptyMap())
  private val initialized = CompletableDeferred<Unit>()

  init {
    scope.launch {
      durable {
        val api = PyToolApi.getInstance()
        if (!api.isStateInitialized(project.projectId())) {
          api.initializeState(project.projectId())
        }
        api.observeEnabledStates(project.projectId()).collect { states ->
          enabled.set(states.associate { it.toolId to it.enabled })
          initialized.complete(Unit)
        }
      }
    }
  }

  suspend fun awaitInitialized() {
    initialized.await()
  }

  fun isEnabled(toolId: PyToolId): Boolean = enabled.get()[toolId] == true

  fun apply(state: PyToolEnabledStateDto) {
    enabled.updateAndGet { it + (state.toolId to state.enabled) }
  }

  companion object {
    fun getInstance(project: Project): PyToolsFrontendState = project.service()
  }
}

internal class PyToolsFrontendStateStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    PyToolsFrontendState.getInstance(project).awaitInitialized()
  }
}
