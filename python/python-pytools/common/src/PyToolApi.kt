// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.common

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@Serializable
@JvmInline
value class PyToolId(val value: String)

@Serializable
data class PyToolRequest(val projectId: ProjectId, val toolId: PyToolId)

@Serializable
data class PyToolsRequest(val projectId: ProjectId, val toolIds: List<PyToolId>)

@Serializable
data class PyToolEnabledStateDto(val toolId: PyToolId, val enabled: Boolean)

@Serializable
data class PyToolSetEnabledRequest(val tool: PyToolRequest, val enabled: Boolean)

@Serializable(with = PyToolConfigurationSerializer::class)
interface PyToolConfigurationDto

@Serializable
data class PyToolSetConfigurationRequest(val tool: PyToolRequest, val configuration: PyToolConfigurationDto)

@Serializable
enum class PyToolActionSource { SETTINGS_TABLE, SETTINGS_DETAIL }

@Serializable
enum class PyToolEventKind { CONFIGURATION_CHANGED, INSTALLED, UPDATED }

@Serializable
data class PyToolLogEventRequest(
  val tool: PyToolRequest,
  val source: PyToolActionSource,
  val event: PyToolEventKind,
)

@Serializable
data class PyToolPathDto(val value: String, val kind: PyToolPathKind)

@Serializable
enum class PyToolPathKind { CUSTOM, DETECTED }

@Serializable
data class PyToolDescriptorDto(
  val minimumSupportedVersion: String? = null,
)

@Serializable
data class PyToolStateDto(
  val toolId: PyToolId,
  val descriptor: PyToolDescriptorDto,
  val enabled: Boolean,
  val path: PyToolPathDto?,
  /** Set only when the tool manager already knows the version of the resolved file; see [PyToolApi.getVersion]. */
  val version: String?,
  val canInstall: Boolean,
  val latestVersion: String? = null,
  val configuration: PyToolConfigurationDto? = null,
  val selectedAsTypeEngine: Boolean = false,
)

/** The resolved path of one tool, without the version or the manager data that a full state carries. */
@Serializable
data class PyToolPathStateDto(val toolId: PyToolId, val path: PyToolPathDto?)

@Serializable
data class PyToolPathRequest(val tool: PyToolRequest, val path: String)

@Serializable
data class PyToolSetPathRequest(val tool: PyToolRequest, val path: String?)

@Serializable
sealed interface PyToolValidationDto {
  @Serializable data class Valid(val version: String?) : PyToolValidationDto
  @Serializable data class Invalid(val message: String) : PyToolValidationDto
}

@Serializable
sealed interface PyToolOperationResultDto {
  @Serializable data class Success(val state: PyToolStateDto) : PyToolOperationResultDto
  @Serializable data class Failure(val message: String) : PyToolOperationResultDto
}

@Serializable
sealed interface PyToolSdkOperationResultDto {
  @Serializable data class Success(val state: PyToolSdkStateDto) : PyToolSdkOperationResultDto
  @Serializable data class Failure(val message: String) : PyToolSdkOperationResultDto
}

@Serializable
data class PyToolSdkDto(val token: String, val label: String)

@Serializable
data class PyToolSdkStateDto(val sdk: PyToolSdkDto, val path: String?, val version: String?)

@Serializable
enum class PyToolDependencyGroupKind { DEPENDENCY_GROUP, OPTIONAL_DEPENDENCY }

@Serializable
data class PyToolDependencyGroupDto(val name: String, val kind: PyToolDependencyGroupKind)

@Serializable
data class PyToolSdkRequest(val tool: PyToolRequest, val sdk: PyToolSdkDto)

@Serializable
data class PyToolSdkInstallRequest(
  val target: PyToolSdkRequest,
  val dependencyGroup: PyToolDependencyGroupDto?,
)

/** Backend tool lifecycle. No EEL, filesystem, SDK, cache, process, manager, or backend entity crosses the wire. */
@ApiStatus.Internal
@Rpc
interface PyToolApi : RemoteApi<Unit> {
  suspend fun isStateInitialized(projectId: ProjectId): Boolean
  suspend fun initializeState(projectId: ProjectId)
  suspend fun observeEnabledStates(projectId: ProjectId): Flow<List<PyToolEnabledStateDto>>
  suspend fun getConfiguration(request: PyToolRequest): PyToolConfigurationDto?
  suspend fun getStates(request: PyToolsRequest): List<PyToolStateDto>
  /**
   * The resolved paths only, so the settings pages can show a known path at once.
   *
   * [getStates] runs the tool manager and a `--version` process per tool, which takes seconds. This
   * call reads the custom path and the detection cache, and it probes no version.
   */
  suspend fun getPaths(request: PyToolsRequest): List<PyToolPathStateDto>

  /**
   * The version of one tool's resolved executable, or `null` when the tool resolves nowhere or reports no
   * usable version.
   *
   * Ask for it where the version is shown. It is free when the tool manager already knows the file, and
   * costs a `<path> --version` run otherwise, which is why [getStates] never resolves it on its own.
   */
  suspend fun getVersion(request: PyToolRequest): String?
  suspend fun setEnabled(request: PyToolSetEnabledRequest): PyToolStateDto
  suspend fun setConfiguration(request: PyToolSetConfigurationRequest): PyToolStateDto
  suspend fun validatePath(request: PyToolPathRequest): PyToolValidationDto
  suspend fun setPath(request: PyToolSetPathRequest): PyToolStateDto
  suspend fun install(request: PyToolRequest): PyToolOperationResultDto
  suspend fun upgrade(request: PyToolRequest): PyToolOperationResultDto
  suspend fun getSdkStates(request: PyToolRequest): List<PyToolSdkStateDto>
  suspend fun getDependencyGroups(request: PyToolSdkRequest): List<PyToolDependencyGroupDto>
  suspend fun installIntoSdk(request: PyToolSdkInstallRequest): PyToolSdkOperationResultDto
  suspend fun logEvent(request: PyToolLogEventRequest)

  companion object {
    suspend fun getInstance(): PyToolApi = RemoteApiProviderService.resolve(remoteApiDescriptor<PyToolApi>())
  }
}

@ApiStatus.Internal
suspend inline fun <reified C : PyToolConfigurationDto> PyToolApi.getConfiguration(request: PyToolRequest): C {
  return getConfiguration(request) as? C
         ?: error("Unexpected configuration for Python tool: ${request.toolId.value}")
}
