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
)

@Serializable
data class PyTypeEngineSelectionRequest(
  val projectId: ProjectId,
  val selected: PyTypeEngineId,
  val disabledToolPackages: Set<String>,
)

@Serializable
data class PyTypeEngineRequest(val projectId: ProjectId, val typeEngineId: PyTypeEngineId)

@Serializable
sealed interface PyTypeEngineOperationResultDto {
  @Serializable
  data class Success(val state: PyTypeEngineStateDto, val path: String?) : PyTypeEngineOperationResultDto

  @Serializable
  data class Failure(val message: String) : PyTypeEngineOperationResultDto
}

@Serializable
enum class PyTypeEngineEvent { SETTINGS_OPENED, STATUS_WIDGET_CLICKED }

@Serializable
data class PyTypeEngineEventRequest(val projectId: ProjectId, val event: PyTypeEngineEvent)

@Internal
@Rpc
interface PyTypeEngineApi : RemoteApi<Unit> {
  suspend fun observeState(projectId: ProjectId): Flow<PyTypeEngineStateDto>

  suspend fun select(request: PyTypeEngineSelectionRequest): PyTypeEngineStateDto

  suspend fun install(request: PyTypeEngineRequest): PyTypeEngineOperationResultDto

  suspend fun logEvent(request: PyTypeEngineEventRequest)

  companion object {
    suspend fun getInstance(): PyTypeEngineApi = RemoteApiProviderService.resolve(remoteApiDescriptor<PyTypeEngineApi>())
  }
}
