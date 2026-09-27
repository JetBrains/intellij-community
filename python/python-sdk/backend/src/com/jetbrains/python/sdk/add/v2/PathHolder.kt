// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.provider.asEelPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString


/**
 *  **DO NOT USE** [toString]!
 *
 *  Each [Eel] have 2 representations:
 *  * MRFS (aka nio [Path]) e.g. `\\wsl$\...`. Use it for IJ code, UI and to browse the filesystem.
 *  * Eel Path (path on remote machine): `/usr/bin/python`. Provide it as an argument to remote processes.
 *
 *  Targets only have [FullPathOnTarget] (`/usr/bin/python`).
 *
 *  To get MRFS use [toStringForUI].
 *  To get remote path use [toStringForExecution]
 *
 *  Please, read https://jetbrains.team/blog/Developing_intuition_about_eel_paths before using this class.
 */
@ApiStatus.Internal
sealed class PathHolder {
  private companion object {
    val log = fileLogger()
  }

  /**
   * This method has no semantics, please use either [toStringForExecution] or [toStringForUI].
   * See class KDoc
   */
  @Suppress("POTENTIALLY_NON_REPORTED_ANNOTATION")
  @Deprecated("Do not use toSting", level = DeprecationLevel.ERROR)
  final override fun toString(): String {
    val me = when (this) {
      is Eel -> "Eel on $path"
      is Target -> "Target on $pathString"
    }
    log.error("Do not call toString on $me")
    return this.toStringForUI()
  }

  /**
   * For targets returns [FullPathOnTarget].
   * For [Eel] returns [Path.toString]
   *
   * To be used in UI.
   */
  abstract fun toStringForUI(): FullPathOnTarget

  /**
   * Path on remote machine, use to execute things.
   */
  abstract fun toStringForExecution(): FullPathOnTarget

  data class Eel(val path: Path) : PathHolder() {

    override fun toStringForUI(): FullPathOnTarget = path.pathString

    override fun toStringForExecution(): FullPathOnTarget = path.asEelPath().toString()
  }

  data class Target(val pathString: FullPathOnTarget) : PathHolder() {

    override fun toStringForUI(): FullPathOnTarget = pathString
    override fun toStringForExecution(): FullPathOnTarget = pathString
  }
}
