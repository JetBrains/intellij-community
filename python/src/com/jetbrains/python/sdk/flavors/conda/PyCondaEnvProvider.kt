// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecService
import com.jetbrains.python.PyBundle
import com.jetbrains.python.TraceContext
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isFailure
import com.jetbrains.python.isSuccess
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.conda.execution.models.CondaEnvInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@ApiStatus.Internal
@IntellijInternalApi
class PyCondaEnvProvider(
  refreshInterval: Duration,
  ttlAfterWrite: Duration,
  executor: Executor = Dispatchers.Default.asExecutor(),
  private val execService: ExecService = ExecService(),
) {
  private val cache: AsyncLoadingCache<BinaryToExec, PyResult<List<PyCondaEnv>>> = Caffeine.newBuilder()
    .executor(executor)
    .refreshAfterWrite(refreshInterval.toJavaDuration())
    .expireAfterWrite(ttlAfterWrite.toJavaDuration())
    .buildAsync { binaryToExec ->
      runBlockingMaybeCancellable {
        withContext(TraceContext(PyBundle.message("trace.context.py.conda.env.provider.cache.update"))) {
          getEnvsInternal(binaryToExec)
        }
      }
    }

  suspend fun getEnvs(binaryToExec: BinaryToExec, forceRefresh: Boolean): PyResult<List<PyCondaEnv>> {
    if (forceRefresh) {
      return cache.synchronous().refresh(binaryToExec).asDeferred().await()
    }

    val currentValue = cache.getIfPresent(binaryToExec)?.asDeferred()?.await()
    return when {
      currentValue?.isSuccess == true -> currentValue
      currentValue?.isFailure == true -> cache.synchronous().refresh(binaryToExec).asDeferred().await()
      else -> cache[binaryToExec].asDeferred().await()
    }
  }

  private suspend fun getEnvsInternal(binaryToExec: BinaryToExec): PyResult<List<PyCondaEnv>> {
    val condaPath = when (binaryToExec) {
      is BinOnEel -> binaryToExec.path.toString()
      is BinOnTarget -> binaryToExec.getLocalExePath().value
    }
    val condaEnvInfo = CondaExecutor.listEnvs(binaryToExec, execService).getOr { return it }
    val condaEnvs = fromEnvsDetails(condaEnvInfo, condaPath) ?: fromEnvsDirs(condaEnvInfo, condaPath)
    return PyResult.success(condaEnvs)
  }

  private fun fromEnvsDetails(condaEnvInfo: CondaEnvInfo, condaPath: FullPathOnTarget): List<PyCondaEnv>? {
    if (condaEnvInfo.envsDetails == null) return null

    val envs = condaEnvInfo.envs.distinctBy { it.trim().lowercase(Locale.getDefault()) }
    return envs.map { envPath ->
      val envInDetails = condaEnvInfo.envsDetails[envPath]

      val (envName, base) = envInDetails?.let { envDetails ->
        envDetails.name.takeIf { it.isNotBlank() } to envDetails.base
      } ?: (null to false)

      constructCondaEnv(envName, envPath, base, condaPath)
    }
  }

  private fun fromEnvsDirs(condaEnvInfo: CondaEnvInfo, condaPath: FullPathOnTarget): List<PyCondaEnv> {
    val condaPrefix = condaEnvInfo.condaPrefix ?: condaPath.removeSuffix("/bin/conda")
    val envs = condaEnvInfo.envs.distinctBy { it.trim().lowercase(Locale.getDefault()) }
    val identities = envs.map { envPath ->
      // Env name is the basename for envs inside of default location
      // envPath should be direct child of envs_dirs to be a NamedEnv
      val isEnvName = condaEnvInfo.envsDirs.any {
        Path.of(it) == Path.of(envPath).parent
      }
      val envName = if (isEnvName)
        Path.of(envPath).name
      else
        null
      val base = envPath.equals(condaPrefix, ignoreCase = true)
      constructCondaEnv(envName, envPath, base, condaPath)
    }

    return identities
  }

  private fun constructCondaEnv(envName: String?, envPath: String, base: Boolean, condaPath: FullPathOnTarget): PyCondaEnv {
    val identity = if (envName != null) {
      PyCondaEnvIdentity.NamedEnv(envName)
    }
    else {
      PyCondaEnvIdentity.UnnamedEnv(envPath, base)
    }

    return PyCondaEnv(identity, condaPath)
  }
}