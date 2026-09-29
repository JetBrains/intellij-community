// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import java.nio.file.Path

/**
 * Sometimes you have a nullable path, but you must still provide an eel.
 * This class *always* has an [eel] and optionally has a [path].
 * If [path] is not `null`, it is *guaranteed* to reside on the same [eel].
 *
 * Use [asEelOrJustPath] to create it, and [toEelFileSystem] to get [FileSystem].
 *
 * A caller that holds a [com.jetbrains.python.project.PyProject] always has a path, so it needs no such wrapper.
 */
internal class EelOrJustPath private constructor(val path: Path?, private val eel: EelDescriptor) {
  internal companion object {
    suspend fun EelOrJustPath.toEelFileSystem(): EelFileSystem = eel.toEelFileSystem()
    fun Path.asEelOrJustPath(): EelOrJustPath = EelOrJustPath(this, getEelDescriptor())
  }
}
