// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelApi
import org.jetbrains.annotations.ApiStatus

/**
 * Bridge for constructing an Eel-backed [FileSystem] from an [EelApi]. The implementation lives in a module that has access
 * to the concrete `EelFileSystem` type; downstream modules (e.g. `intellij.python.hatch`) reach it through this service.
 */
@ApiStatus.Internal
interface EelFileSystemFactory {
  fun create(eelApi: EelApi): FileSystem<PathHolder.Eel>

  companion object {
    fun getInstance(): EelFileSystemFactory = service()
  }
}

/**
 * Returns a [FileSystem] backed by this [EelApi]. Equivalent to `EelFileSystemFactory.getInstance().create(this)`.
 */
@ApiStatus.Internal
fun EelApi.toFileSystem(): FileSystem<PathHolder.Eel> = EelFileSystemFactory.getInstance().create(this)