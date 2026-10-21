// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Bridge for constructing a [FileSystem] on an Eel machine or on a target. The implementation lives in a module that has access
 * to the concrete `EelFileSystem` and `TargetFileSystem` types; downstream modules (e.g. `intellij.python.hatch`) reach it
 * through this service.
 */
@ApiStatus.Internal
interface FileSystemFactory {
  fun create(eelApi: EelApi): FileSystemWithEel

  /** A [FileSystem] on the target [target], for a module that cannot reach the concrete target type. */
  fun create(target: TargetEnvironmentConfiguration): FileSystem<PathHolder.Target>

  companion object {
    fun getInstance(): FileSystemFactory = service()

    /**
     * The [FileSystem] of [sdk]: the target of [PyTargetAwareAdditionalData], else the Eel machine of [Sdk.getHomePath].
     * A target SDK always uses its target, never the Eel machine.
     */
    suspend fun createForSdk(sdk: Sdk): FileSystem<*> {
      val instance = getInstance()
      val additionalData = sdk.pySdkAdditionalData
      return if (additionalData is PyTargetAwareAdditionalData) {
        instance.create(additionalData.targetEnvironmentConfiguration!!)
      }
      else {
        instance.create(Path.of(sdk.homePath!!).getEelDescriptor().toEelApi())
      }
    }
  }
}

/**
 * Returns a [FileSystem] backed by this [EelApi]. Equivalent to `FileSystemFactory.getInstance().create(this)`.
 */
@ApiStatus.Internal
fun EelApi.toFileSystem(): FileSystemWithEel = FileSystemFactory.getInstance().create(this)

/**
 * Returns the [FileSystem] of this SDK. Equivalent to `FileSystemFactory.createForSdk(this)`.
 * A target SDK uses its target and never the Eel machine. Other SDKs use the Eel machine of [Sdk.getHomePath].
 */
@ApiStatus.Internal
suspend fun Sdk.toFileSystem(): FileSystem<*> = FileSystemFactory.createForSdk(this)