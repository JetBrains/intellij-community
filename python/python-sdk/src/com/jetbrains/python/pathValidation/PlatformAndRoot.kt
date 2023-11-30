// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pathValidation

import com.intellij.execution.Platform
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.nio.file.Path

/**
 * Against what path must be validated. [root] is now nullable since no targets provide nio access, but that will be fixed soon.
 */
class PlatformAndRoot private constructor(val root: Path?, val platform: Platform) {
  companion object {
    /**
     * Local system
     */
    val local: PlatformAndRoot = PlatformAndRoot(Path.of(""), Platform.current())

    /**
     * Creates [PlatformAndRoot] for [TargetEnvironmentConfiguration]. If null then returns either [local] or [platform] only depending
     * on [defaultIsLocal]
     */
    @RequiresBackgroundThread
    fun TargetEnvironmentConfiguration?.getPlatformAndRoot(defaultIsLocal: Boolean = true): PlatformAndRoot {
      val unknownTarget = PlatformAndRoot(null, Platform.UNIX)
      return when {
        this == null -> if (defaultIsLocal) local else unknownTarget
        else -> unknownTarget //Non-null target is never local
      }
    }

  }
}