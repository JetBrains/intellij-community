// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.NativePath
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

/**
 * Runs the terminal customizers ([org.jetbrains.plugins.terminal.LocalTerminalCustomizer] and
 * [ShellExecOptionsCustomizer]) on the backend.
 *
 * In the RemDev scenario, the terminal startup options are configured on the frontend,
 * but the customizer extensions are registered only on the backend. So the frontend
 * delegates customization to the backend via this API.
 */
@ApiStatus.Internal
@Rpc
interface TerminalExecOptionsCustomizationRemoteApi : RemoteApi<Unit> {
  suspend fun customizeExecOptions(request: TerminalExecOptionsCustomizationRequest): TerminalExecOptionsCustomizationResponse

  companion object {
    @JvmStatic
    suspend fun getInstance(): TerminalExecOptionsCustomizationRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TerminalExecOptionsCustomizationRemoteApi>())
    }
  }
}

/**
 * Everything the backend needs to run the customizers.
 *
 * @param eelDescriptor is [Transient]: it is present only in the monolith (where the shell process may run in a
 * different environment than the project). In the RemDev scenario it is `null` because [EelDescriptor] is not
 * serializable, and the backend then assumes [workingDirectory] belongs to the backend project environment.
 * @param workingDirectory expected to be a valid native path in the environment of [eelDescriptor].
 */
@ApiStatus.Internal
@Serializable
data class TerminalExecOptionsCustomizationRequest(
  val projectId: ProjectId,
  val shellCommand: List<String>,
  val workingDirectory: @NativePath String,
  val envVariables: Map<String, String>,
  val shellIntegrationAvailable: Boolean,
  @Transient val eelDescriptor: EelDescriptor? = null,
)

/**
 * The customized shell command and environment variables produced by the customizers.
 */
@ApiStatus.Internal
@Serializable
data class TerminalExecOptionsCustomizationResponse(
  val shellCommand: List<String>,
  val envVariables: Map<String, String>,
)
