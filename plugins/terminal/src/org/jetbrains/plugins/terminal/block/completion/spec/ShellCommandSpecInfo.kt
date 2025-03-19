// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import com.intellij.terminal.completion.spec.ShellCommandSpec
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class ShellCommandSpecInfo private constructor(
  val spec: ShellCommandSpec,
  val conflictStrategy: ShellCommandSpecConflictStrategy,
) {
  override fun toString(): String {
    return "ShellCommandSpecInfo(spec=$spec, conflictStrategy=$conflictStrategy)"
  }

  companion object {
    fun create(
      spec: ShellCommandSpec,
      conflictStrategy: ShellCommandSpecConflictStrategy
    ): ShellCommandSpecInfo {
      return ShellCommandSpecInfo(spec, conflictStrategy)
    }
  }
}

