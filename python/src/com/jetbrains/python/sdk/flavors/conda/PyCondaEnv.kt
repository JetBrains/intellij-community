// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.python.community.execService.BinaryToExec
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.conda.TargetCommandExecutor
import com.jetbrains.python.sdk.conda.createCondaSdkFromExistingEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv.Companion.getEnvs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.seconds

/**
 * TODO: Once we get rid of [TargetCommandExecutor] and have access to [TargetEnvironmentConfiguration] use it validate conda binary in [getEnvs]
 * @see `PyCondaTest`
 */
@ApiStatus.Internal
data class PyCondaEnv(
  val envIdentity: PyCondaEnvIdentity,
  val fullCondaPathOnTarget: FullPathOnTarget,
) {
  companion object {

    /**
     * The logic is the following:
     *
     * - If an explicit refresh is triggered, ask the cache to reload the value
     * - If a value is present in the cache, a refresh is triggered if the refresh interval has passed, and the old value is returned:
     *   - If it's an error, let's try to reload (we may succeed this time)
     *   - If it's a success, return this value
     * - If a value is not present in the cache, it will be calculated
     *
     * @return list of conda environments
     */
    @ApiStatus.Internal
    @JvmOverloads
    suspend fun getEnvs(binaryToExec: BinaryToExec, forceRefresh: Boolean = false): PyResult<List<PyCondaEnv>> =
      service<CondaEnvService>().getEnvs(binaryToExec, forceRefresh)

    suspend fun createEnv(command: PyCondaCommand, newCondaEnvInfo: NewCondaEnvRequest): PyResult<Unit> {
      return newCondaEnvInfo.create(command.asBinaryToExec())
    }
  }

  suspend fun createSdkFromThisEnv(targetConfig: TargetEnvironmentConfiguration?, existingSdk: List<Sdk>, project: Project? = null): Sdk =
    PyCondaCommand(fullCondaPathOnTarget, targetConfig).createCondaSdkFromExistingEnv(envIdentity, existingSdk, project)


  /**
   * Add conda prefix to [targetedCommandLineBuilder]
   */
  fun addCondaToTargetBuilder(targetedCommandLineBuilder: TargetedCommandLineBuilder) {
    targetedCommandLineBuilder.apply {
      setExePath(fullCondaPathOnTarget)
      addParameter("run")
      when (val identity = this@PyCondaEnv.envIdentity) {
        is PyCondaEnvIdentity.UnnamedEnv -> {
          addParameter("-p")
          addParameter(identity.envPath) // TODO: Escape. Shouldn't target have something like "addEscaped"?
        }
        is PyCondaEnvIdentity.NamedEnv -> {
          addParameter("-n")
          addParameter(identity.envName)
        }
      }
      // Otherwise we wouldn't have interactive output (for console etc.)
      addParameter("--no-capture-output")
    }
  }

  override fun toString(): String = "$envIdentity@$fullCondaPathOnTarget"
}

@OptIn(IntellijInternalApi::class)
@Service(Service.Level.APP)
private class CondaEnvService(scope: CoroutineScope) {
  private val _condaEnvProviderImpl: Deferred<PyCondaEnvProvider> = scope.async {
    PyCondaEnvProvider(
      refreshInterval = RegistryManager.getInstanceAsync().intValue("python.conda.envs.refresh.seconds").seconds,
      ttlAfterWrite = RegistryManager.getInstanceAsync().intValue("python.conda.envs.cache.ttl.seconds").seconds,
    )
  }
  private suspend fun condaEnvProvider() = _condaEnvProviderImpl.await()

  suspend fun getEnvs(binaryToExec: BinaryToExec, forceRefresh: Boolean): PyResult<List<PyCondaEnv>> =
    condaEnvProvider().getEnvs(binaryToExec, forceRefresh)
}
