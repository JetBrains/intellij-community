// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec

import org.jetbrains.annotations.ApiStatus

/**
 * Different strategies of resolving conflicts when there are a couple of specs denoting the single shell command.
 */
@ApiStatus.Experimental
enum class ShellCommandSpecConflictStrategy {
  /**
   * Only this spec will be effective, all others will be ignored.
   * If there are two or more such specs, the result is undefined.
   */
  REPLACE,

  /**
   * Strategy for base specs. Specs with this strategy can be replaced with other specs.
   */
  DEFAULT
}