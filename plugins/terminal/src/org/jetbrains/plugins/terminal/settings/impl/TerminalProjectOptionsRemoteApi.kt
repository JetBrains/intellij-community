// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.settings.impl

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Frontend → backend migration RPC for [TerminalProjectOptionsMigration].
 *
 * In the new frontend-only terminal mode, the project options provider is stored on the frontend.
 * Custom values previously persisted by the user on the backend would be lost without migration.
 * This RPC fetches the user-customized values from the backend so the frontend can apply them at once.
 */
@ApiStatus.Internal
@Rpc
interface TerminalProjectOptionsRemoteApi : RemoteApi<Unit> {
  /**
   * Returns custom project options stored on the backend. Fields are `null` when the user has not
   * set a custom value (i.e. the backend would fall back to a default).
   */
  suspend fun getProjectOptions(projectId: ProjectId): TerminalProjectOptionsDto

  companion object {
    suspend fun getInstance(): TerminalProjectOptionsRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalProjectOptionsRemoteApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class TerminalProjectOptionsDto(
  val startingDirectory: String?,
  val shellPath: String?,
  val envData: EnvironmentVariablesDataDto?,
)

@ApiStatus.Internal
@Serializable
data class EnvironmentVariablesDataDto(
  val envs: Map<String, String>,
  val isPassParentEnvs: Boolean,
)