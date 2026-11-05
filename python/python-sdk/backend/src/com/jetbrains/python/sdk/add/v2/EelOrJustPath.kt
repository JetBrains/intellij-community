// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Sometimes you have a nullable path, but you must still provide an eel.
 * This class *always* has an [eel] and optionally has a [path].
 * If [path] is not `null`, it is *guaranteed* to reside on the same [eel].
 *
 * Use [asEelOrJustPath] to create it, and [toFileSystem] to get [FileSystem].
 *
 * A caller that holds a [com.jetbrains.python.project.PyProject] always has a path, so it needs no such wrapper.
 */
@ApiStatus.Internal
class EelOrJustPath private constructor(val path: Path?, val eel: EelDescriptor) {
  companion object {
    /** The [FileSystem] of the Eel machine of this path. */
    suspend fun EelOrJustPath.toFileSystem(): FileSystemWithEel = eel.toEelApi().toFileSystem()
    fun Path.asEelOrJustPath(): EelOrJustPath = EelOrJustPath(this, getEelDescriptor())
  }
}
