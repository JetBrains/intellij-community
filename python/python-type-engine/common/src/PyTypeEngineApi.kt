// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.typeEngine.common

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

@Serializable
enum class PyTypeEngineId(val packageName: String) {
  PYCHARM(""),
  PYREFLY("pyrefly"),
  TY("ty"),
}

@Serializable
data class PyTypeEngineStateDto(
  val selected: PyTypeEngineId,
  val supported: Set<PyTypeEngineId>,
  val installed: Set<PyTypeEngineId>,
  /** The engines that run a server of their own. The External Tools tool of the same package does not take part. */
  val ownServer: Set<PyTypeEngineId> = emptySet(),
)

@Serializable
data class PyTypeEngineRequest(val projectId: ProjectId, val typeEngineId: PyTypeEngineId)

/** The editor features of a type engine that the Type Engine page edits. `null` means that the engine has no such feature. */
@Serializable
data class PyTypeEngineFeaturesDto(val inlayHints: Boolean?, val documentation: Boolean?)

@Serializable
data class PyTypeEngineSetFeaturesRequest(val engine: PyTypeEngineRequest, val features: PyTypeEngineFeaturesDto)

@Serializable
data class PyTypeEngineSelectionRequest(
  val projectId: ProjectId,
  val selected: PyTypeEngineId,
  val disabledToolPackages: Set<String>,
)

@Serializable
enum class PyTypeEngineEvent { SETTINGS_OPENED, STATUS_WIDGET_CLICKED }

@Serializable
data class PyTypeEngineEventRequest(val projectId: ProjectId, val event: PyTypeEngineEvent)

@Internal
@Rpc
interface PyTypeEngineApi : RemoteApi<Unit> {
  suspend fun observeState(projectId: ProjectId): Flow<PyTypeEngineStateDto>

  suspend fun select(request: PyTypeEngineSelectionRequest): PyTypeEngineStateDto

  /** Returns the editor features of the engine, or `null` when the engine has none to edit. */
  suspend fun getFeatures(request: PyTypeEngineRequest): PyTypeEngineFeaturesDto?

  /** Writes the editor features of the engine. A feature that the engine does not have stays as it is. */
  suspend fun setFeatures(request: PyTypeEngineSetFeaturesRequest)

  suspend fun logEvent(request: PyTypeEngineEventRequest)

  companion object {
    suspend fun getInstance(): PyTypeEngineApi = RemoteApiProviderService.resolve(remoteApiDescriptor<PyTypeEngineApi>())
  }
}
