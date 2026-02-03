// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecService
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isFailure
import com.jetbrains.python.isSuccess
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.future.asDeferred
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
        getEnvsInternal(binaryToExec)
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
    val info = CondaExecutor.listEnvs(binaryToExec, execService).getOr { return it }
    val condaPrefix = info.condaPrefix ?: condaPath.removeSuffix("/bin/conda")
    val envs = info.envs.distinctBy { it.trim().lowercase(Locale.getDefault()) }
    val identities = envs.map { envPath ->
      // Env name is the basename for envs inside of default location
      // envPath should be direct child of envs_dirs to be a NamedEnv
      val isEnvName = info.envsDirs.any {
        Path.of(it) == Path.of(envPath).parent
      }
      val envName = if (isEnvName)
        Path.of(envPath).name
      else
        null
      val base = envPath.equals(condaPrefix, ignoreCase = true)
      val identity = if (envName != null) {
        PyCondaEnvIdentity.NamedEnv(envName)
      }
      else {
        PyCondaEnvIdentity.UnnamedEnv(envPath, base)
      }
      PyCondaEnv(identity, condaPath)
    }

    return PyResult.success(identities)
  }
}