// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.components.Service
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * The [FileSystem] of each legacy target, cached for a short time because creating one probes the target.
 *
 * A module outside the Python plugin opts in to [PyInternalExecApi] to use it. The concrete file system stays internal,
 * so a caller sees only the [FileSystem] interface.
 */
@Service(Service.Level.APP)
@PyInternalExecApi
@ApiStatus.Internal
class TargetFileSystemCache {

  private data class CacheKey(private val targetId: String, private val runtimeProperties: PythonLanguageRuntimeConfiguration.State)

  private val cache: Cache<CacheKey, TargetFileSystem> = Caffeine.newBuilder()
    .expireAfterWrite(5.minutes.toJavaDuration())
    .build()

  /**
   * The file system on the target [targetConfig]. [runtimeConfiguration] holds the Python settings of the target, such
   * as the interpreter path. A caller that reads none of them passes a new [PythonLanguageRuntimeConfiguration].
   */
  fun getOrCreate(
    targetConfig: TargetEnvironmentConfiguration,
    runtimeConfiguration: PythonLanguageRuntimeConfiguration,
  ): FileSystem<PathHolder.Target> = cache.get(CacheKey(targetConfig.uuid, runtimeConfiguration.state)) {
    TargetFileSystem(targetConfig, runtimeConfiguration)
  }
}