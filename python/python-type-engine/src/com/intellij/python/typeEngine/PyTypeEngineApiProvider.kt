// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.python.lsp.core.listener.PyLspListener
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProjectSettings
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineProvider
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineType
import com.intellij.python.lsp.core.typeEngine.PyTypeEngineUsageCollector
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyToolsState
import com.intellij.python.typeEngine.common.PyTypeEngineApi
import com.intellij.python.typeEngine.common.PyTypeEngineEvent
import com.intellij.python.typeEngine.common.PyTypeEngineEventRequest
import com.intellij.python.typeEngine.common.PyTypeEngineId
import com.intellij.python.typeEngine.common.PyTypeEngineOperationResultDto
import com.intellij.python.typeEngine.common.PyTypeEngineRequest
import com.intellij.python.typeEngine.common.PyTypeEngineSelectionRequest
import com.intellij.python.typeEngine.common.PyTypeEngineStateDto
import com.intellij.python.ty.TyUtil
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackageSnapshot
import com.jetbrains.python.psi.types.PyTypeEngineSettingsModificationTracker
import com.jetbrains.python.sdk.PySdkListener
import com.jetbrains.python.sdk.pythonSdk
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class PyTypeEngineApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<PyTypeEngineApi>()) { PyTypeEngineApiImpl }
  }
}

private object PyTypeEngineApiImpl : PyTypeEngineApi {
  override suspend fun observeState(projectId: ProjectId): Flow<PyTypeEngineStateDto> = callbackFlow {
    val project = projectId.findProject()
    val connection = project.messageBus.connect()
    fun publish() {
      trySend(state(project))
    }

    connection.subscribe(PyLspListener.TOPIC, object : PyLspListener {
      override fun onTypeSettingsChange() = publish()
    })
    connection.subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun modulesAdded(project: Project, modules: List<Module?>) = publish()
      override fun moduleRemoved(project: Project, module: Module) = publish()
    })
    connection.subscribe(PySdkListener.TOPIC, object : PySdkListener {
      override fun moduleSdkUpdated(module: Module, prevSdk: Sdk?, newSdk: Sdk?) = publish()
    })
    connection.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(sdk: Sdk) = publish()
    })
    publish()
    awaitClose { connection.disconnect() }
  }

  override suspend fun select(request: PyTypeEngineSelectionRequest): PyTypeEngineStateDto {
    val project = request.projectId.findProject()
    val toolsState = PyToolsState.getInstance(project)
    PyTool.findByPackageName(request.selected.packageName)?.let { tool ->
      if (!toolsState.isEnabled(tool)) {
        toolsState.setEnabled(tool, true)
      }
    }
    request.disabledToolPackages.forEach { packageName ->
      PyTool.findByPackageName(packageName)?.let { tool ->
        if (toolsState.isEnabled(tool)) {
          toolsState.setEnabled(tool, false)
          tool.onEnabledChanged(project, false)
        }
      }
    }

    val settings = PyTypeEngineProjectSettings.getInstance(project)
    val selected = request.selected.toBackendType()
    if (settings.typeEngine != selected) {
      settings.typeEngine = selected
      PyTypeEngineUsageCollector.logEngineChanged(project, selected)
      PyTypeEngineSettingsModificationTracker.getInstance(project).incModificationCount()
    }
    return state(project)
  }

  override suspend fun install(request: PyTypeEngineRequest): PyTypeEngineOperationResultDto {
    val project = request.projectId.findProject()
    if (request.typeEngineId != PyTypeEngineId.TY) {
      return PyTypeEngineOperationResultDto.Failure("The type engine does not support direct installation")
    }
    val path = TyUtil.downloadTyBinary()
      ?: return PyTypeEngineOperationResultDto.Failure("Cannot install ty")
    return PyTypeEngineOperationResultDto.Success(state(project), path.toString())
  }

  override suspend fun logEvent(request: PyTypeEngineEventRequest) {
    val project = request.projectId.findProject()
    when (request.event) {
      PyTypeEngineEvent.SETTINGS_OPENED -> PyTypeEngineUsageCollector.logSettingsOpened(project)
      PyTypeEngineEvent.STATUS_WIDGET_CLICKED -> PyTypeEngineUsageCollector.logStatusBarWidgetClicked(project)
    }
  }
}

private fun state(project: Project): PyTypeEngineStateDto {
  val supported = PyTypeEngineProvider.getSupportedTypes(project).mapTo(mutableSetOf()) { it.toFrontendId() }
  val sdks = project.modules.mapNotNull { it.pythonSdk }.distinct()
  val installed = supported.filterTo(mutableSetOf()) { type ->
    when (type) {
      PyTypeEngineId.PYCHARM -> true
      PyTypeEngineId.TY -> TyUtil.isTyInstalled()
      else -> sdks.any { sdk -> PythonPackageManager.forSdk(project, sdk).hasInstalledPackageSnapshot(type.packageName) }
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
