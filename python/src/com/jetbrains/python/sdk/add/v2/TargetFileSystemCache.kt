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

@Service(Service.Level.APP)
@ApiStatus.Internal
@PyInternalExecApi
class TargetFileSystemCache {

  private data class CacheKey(private val targetId: String, private val runtimeProperties: PythonLanguageRuntimeConfiguration.State)

  private val cache: Cache<CacheKey, TargetFileSystem> = Caffeine.newBuilder()
    .expireAfterWrite(5.minutes.toJavaDuration())
    .build()

  fun getOrCreate(
    targetConfig: TargetEnvironmentConfiguration,
    runtimeConfiguration: PythonLanguageRuntimeConfiguration,
  ): TargetFileSystem = cache.get(CacheKey(targetConfig.uuid, runtimeConfiguration.state)) {
    TargetFileSystem(targetConfig, runtimeConfiguration)
  }
}