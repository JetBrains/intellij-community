// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellSuggestionType
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * DSL for declaring [com.intellij.terminal.completion.spec.ShellCompletionSuggestion].
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellCompletionSuggestionContext : ShellSuggestionContext {
  /**
   * Used for now mostly to automatically configure the icon.
   */
  fun type(type: ShellSuggestionType)

  /**
   * Custom icon instead of autodetected from [type].
   */
  fun icon(icon: Icon)

  /**
   * Position inside the [ShellRuntimeContext.typedPrefix] string after which this suggestion should be applied.
   * For example,
   * 1. If typed prefix is `bra` and suggestion name is `branch`, then the replacement index should be `0`,
   * because we need to fully replace the `bra` prefix with `branch`.
   * 2. If typed prefix is `foo/b` and suggestion name is `bar` (we want to suggest the part of the path after `/`),
   * then the replacement index should be `4`.
   */
  fun prefixReplacementIndex(index: Int)

  /**
   * Marks this suggestion to not show it in the completion popup.
   * It may be necessary to specify that this suggestion is also a valid value for the argument.
   * So parser will be able to distinguish it and not mark it as something unknown.
   *
   * For example, if there is a directory suggestion, then it may have a trailing file separator or may not.
   * Both options are acceptable, but only one of them should be shown in the completion popup.
   */
  fun hidden()

  /**
   * By default, the name or [insertValue] is automatically escaped according to the shell syntax
   * (e.g., handling whitespaces or special characters) before being inserted.
   *
   * Marks this suggestion to insert the name or [insertValue] exactly as provided.
   */
  fun noEscaping()
}