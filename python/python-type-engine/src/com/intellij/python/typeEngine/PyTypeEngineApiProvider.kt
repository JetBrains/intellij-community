// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.python.lsp.core.listener.PyLspListener
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUsageCollector
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.pytools.backend.ProjectLevelPyTool
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.pytools.backend.setEnabledOn
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.ty.TyUtil
import com.intellij.python.typeEngine.common.PyTypeEngineApi
import com.intellij.python.typeEngine.common.PyTypeEngineEvent
import com.intellij.python.typeEngine.common.PyTypeEngineEventRequest
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.python.typeEngine.common.PyTypeEngineOperationResultDto
import com.intellij.python.typeEngine.common.PyTypeEngineRequest
import com.intellij.python.typeEngine.common.PyTypeEngineSelectionRequest
import com.intellij.python.typeEngine.common.PyTypeEngineStateDto
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackageSnapshot
import com.jetbrains.python.psi.types.PyTypeEngineSettingsModificationTracker
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class PyTypeEngineApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<PyTypeEngineApi>()) { PyTypeEngineApiImpl }
  }
}

private object PyTypeEngineApiImpl : PyTypeEngineApi {
  override suspend fun observeState(projectId: ProjectId): Flow<PyTypeEngineStateDto> {
    val project = projectId.findProject()
    val model = EvoPyProjectModel.getInstance(project)
    return stateChanges(project)
      .buffer(Channel.CONFLATED)
      .map {
        // Waits for the first snapshot, so a state is never computed without interpreters.
        val snapshot = model.snapshot()
        readAction { state(project, snapshot) }
      }
  }

  /**
   * Selects the engine, then turns off the tools that the request names.
   *
   * The selection does not turn on the tool of the selected engine.
   */
  override suspend fun select(request: PyTypeEngineSelectionRequest): PyTypeEngineStateDto {
    val project = request.projectId.findProject()
    val settings = PyTypeEngineProjectSettings.getInstance(project)
    val selected = request.selected.toBackendType()
    if (settings.typeEngine != selected) {
      settings.typeEngine = selected
      PyTypeEngineUsageCollector.logEngineChanged(project, selected)
      PyTypeEngineSettingsModificationTracker.getInstance(project).incModificationCount()
    }

    // The engine goes first. Turning off the tool of the selected engine also deselects that engine.
    val toolsState = PyToolsState.getInstance(project)
    request.disabledToolPackages.forEach { packageName ->
      ProjectLevelPyTool.findByPackageName(packageName)?.let { tool ->
        if (toolsState.isEnabled(tool)) {
          tool.setEnabledOn(project, false)
        }
      }
    }
    return currentState(project)
  }

  override suspend fun install(request: PyTypeEngineRequest): PyTypeEngineOperationResultDto {
    val project = request.projectId.findProject()
    if (request.typeEngineId != PyTypeEngineId.TY) {
      return PyTypeEngineOperationResultDto.Failure("The type engine does not support direct installation")
    }
    val path = TyUtil.downloadTyBinary()
      ?: return PyTypeEngineOperationResultDto.Failure("Cannot install ty")
    return PyTypeEngineOperationResultDto.Success(currentState(project), path.toString())
  }

  override suspend fun logEvent(request: PyTypeEngineEventRequest) {
    val project = request.projectId.findProject()
    when (request.event) {
      PyTypeEngineEvent.SETTINGS_OPENED -> PyTypeEngineUsageCollector.logSettingsOpened(project)
      PyTypeEngineEvent.STATUS_WIDGET_CLICKED -> PyTypeEngineUsageCollector.logStatusBarWidgetClicked(project)
    }
  }
}

/**
 * Signals a possible change of the type engine state. A Python project that arrives or leaves lands in a new snapshot.
 * The listeners do not compute the state, because some events come inside a write action.
 */
private fun stateChanges(project: Project): Flow<Unit> = callbackFlow {
  val connection = project.messageBus.connect()
  fun publish() {
    trySend(Unit)
  }

  connection.subscribe(PyLspListener.TOPIC, object : PyLspListener {
    override fun onTypeSettingsChange() = publish()
  })
  connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
    override fun packagesChanged(interpreter: PythonInterpreter) = publish()
  })
  // A new interpreter of a Python project lands in a new snapshot, and the state reads the interpreters from it.
  val snapshots = launch { EvoPyProjectModel.getInstance(project).snapshotFlow().collect { publish() } }
  publish()
  awaitClose {
    snapshots.cancel()
    connection.disconnect()
  }
}

/** The state of [project] with the interpreters of the current snapshot. Waits for the first snapshot. */
private suspend fun currentState(project: Project): PyTypeEngineStateDto {
  val snapshot = EvoPyProjectModel.getInstance(project).snapshot()
  return readAction { state(project, snapshot) }
}

/** The state of [project], with the interpreters that [snapshot] gives its Python projects. */
private fun state(project: Project, snapshot: EvoPyProjectModel.Snapshot): PyTypeEngineStateDto {
  val supported = PyTypeEngineProvider.getSupportedTypes(project).mapTo(mutableSetOf()) { it.toFrontendId() }
  val installed = supported.filterTo(mutableSetOf()) { type ->
    when (type) {
      PyTypeEngineId.PYCHARM -> true
      PyTypeEngineId.TY -> TyUtil.isTyInstalled()
      else -> snapshot.interpreters.any { interpreter ->
        PythonPackageManager.forPythonInterpreter(project, interpreter).hasInstalledPackageSnapshot(type.packageName)
      }
    }
  }
  return PyTypeEngineStateDto(
    selected = PyTypeEngineProjectSettings.getInstance(project).typeEngine.toFrontendId(),
    supported = supported,
    installed = installed,
  )
}

private fun PyTypeEngineType.toFrontendId(): PyTypeEngineId = PyTypeEngineId.valueOf(name)

private fun PyTypeEngineId.toBackendType(): PyTypeEngineType = PyTypeEngineType.valueOf(name)
