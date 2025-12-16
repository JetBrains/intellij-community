// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.spec.dsl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

/**
 * DSL for specifying common variables of [ShellCommandContext], [ShellOptionContext] and [ShellCompletionSuggestionContext].
 */
@ApiStatus.Experimental
@ShellCommandSpecDsl
sealed interface ShellSuggestionContext {
  /**
   * Sets the string to be shown in the completion popup instead of the command / option name.
   */
  fun displayName(name: String)

  /**
   * Text to be shown in the documentation popup for this command/option.
   *
   * **Documentation popup currently is not supported in the Reworked Terminal.**
   */
  fun description(@Nls text: String)

  /**
   * Text to be shown in the documentation popup for this command/option.
   *
   * **Documentation popup currently is not supported in the Reworked Terminal.**
   */
  fun description(supplier: Supplier<@Nls String>)

  /**
   * The string to be inserted on completion instead of the command / option name.
   * Supports specifying caret position after completion item insertion in a form `some{caret}item`.
   * In this example `someitem` text will be inserted and caret is placed between `some` and `item`.
   */
  fun insertValue(value: String)

  @set:Deprecated("Please use priority() method instead")
  var priority: Int

  /**
   * Int from 0 to 100 with default 50.
   * Allows specifying the order of the items in the completion popup.
   * The greater the number, the closer the item will be to the first place.
   */
  fun priority(priority: Int)
}