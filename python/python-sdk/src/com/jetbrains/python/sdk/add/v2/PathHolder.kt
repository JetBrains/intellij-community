// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.FullPathOnTarget
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
sealed interface PathHolder {
  data class Eel(val path: Path) : PathHolder {
    override fun toString(): String {
      return path.pathString
    }
  }

  data class Target(val pathString: FullPathOnTarget) : PathHolder {
    override fun toString(): String {
      return pathString
    }
  }
}
