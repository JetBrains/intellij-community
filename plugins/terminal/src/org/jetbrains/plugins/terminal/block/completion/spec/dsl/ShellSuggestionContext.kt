// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

/**
 * DSL for specifying common variables of [ShellCommandContext] and [ShellOptionContext].
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellSuggestionContext {
  val names: List<@NonNls String>

  /**
   * The string to be shown in the completion popup instead of command/option name.
   */
  var displayName: String?

  /**
   * Text to be shown in the documentation popup for this command/option.
   */
  fun description(@Nls text: String)

  /**
   * Text to be shown in the documentation popup for this command/option.
   */
  fun description(supplier: Supplier<@Nls String>)

  /**
   * The string to be inserted on completion instead of command/option name.
   * Supports specifying caret position after completion item insertion in a form `some{caret}item`.
   * In this example `someitem` text will be inserted and caret is placed between `some` and `item`.
   */
  var insertValue: String?

  /**
   * Int from 0 to 100 with default 50.
   * Allows specifying the order of the items in the completion popup.
   * The greater the number, the closer the item will be to the first place.
   */
  var priority: Int
}