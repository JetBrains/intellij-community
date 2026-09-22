// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pytools.common

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
data class PyToolEnabledStateDto(val toolId: PyToolId, val enabled: Boolean)

@Serializable
data class PyToolSetEnabledRequest(val tool: PyToolRequest, val enabled: Boolean)

/**
 * One external tool's configuration on the wire.
 *
 * An open hierarchy, not a sealed one: each tool declares its own subtype in its own module, so
 * [PyToolConfigurationSerializer] resolves the concrete type through an extension point rather than a
 * closed `when`. A payload it cannot resolve — a frontend newer than the backend — is carried opaquely
 * and dropped before it reaches a tool.
 */
@Serializable(with = PyToolConfigurationSerializer::class)
interface PyToolConfigurationDto

@Serializable
data class PyToolSetConfigurationRequest(val tool: PyToolRequest, val configuration: PyToolConfigurationDto)

/**
 * Per-project state of the tools that have any: whether the user enabled them, and their configuration.
 *
 * Separate from [PyToolApi] because only a `ProjectLevelPyTool` has either. A package manager is never
 * enabled or disabled per project and has nothing to configure, so its tool id never reaches these calls and
 * the backend never has to ask whether it did.
 */
@ApiStatus.Internal
@Rpc
interface ProjectLevelPyToolApi : RemoteApi<Unit> {
  /** Whether the enabled-state store already holds this project's tools. */
  suspend fun isStateInitialized(projectId: ProjectId): Boolean

  /** Seeds the enabled-state store from each tool's legacy settings. One-way; see `migrateLegacyState`. */
  suspend fun initializeState(projectId: ProjectId)

  /** The enabled flag of every tool, re-emitted whenever one changes. */
  suspend fun observeEnabledStates(projectId: ProjectId): Flow<List<PyToolEnabledStateDto>>

  /** Enables or disables a tool, runs its lifecycle hook, and answers with its refreshed state. */
  suspend fun setEnabled(request: PyToolSetEnabledRequest): PyToolStateDto

  /** The tool's current configuration, or `null` when the id names no tool with a configuration. */
  suspend fun getConfiguration(request: PyToolRequest): PyToolConfigurationDto?

  /** Applies a configuration the user edited. A configuration of the wrong type for the tool is ignored. */
  suspend fun setConfiguration(request: PyToolSetConfigurationRequest)

  companion object {
    suspend fun getInstance(): ProjectLevelPyToolApi = RemoteApiProviderService.resolve(remoteApiDescriptor<ProjectLevelPyToolApi>())
  }
}

@ApiStatus.Internal
suspend inline fun <reified C : PyToolConfigurationDto> ProjectLevelPyToolApi.getConfiguration(request: PyToolRequest): C {
  return getConfiguration(request) as? C
         ?: error("Unexpected configuration for Python tool: ${request.toolId.value}")
}
