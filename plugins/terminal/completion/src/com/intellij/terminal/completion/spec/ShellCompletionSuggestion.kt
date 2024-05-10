package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * Base interface for shell command, option and argument value suggestions.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellCompletionSuggestion {
  /**
   * The strings to be shown in the completion popup and inserted on completion.
   */
  val names: List<@NonNls String>

  /**
   * Used for now only to automatically configure the icon.
   */
  val type: ShellSuggestionType

  /**
   * The string to be shown in the completion popup instead of [names] if specified.
   */
  val displayName: @NonNls String?

  /**
   * Text to be shown in the documentation popup.
   */
  val description: @Nls String?

  /**
   * The string to be inserted on completion instead of [names] if specified.
   * Supports specifying caret position after completion item insertion in a form `some{caret}item`.
   * In this example `someitem` text will be inserted and caret is placed between `some` and `item`.
   */
  val insertValue: String?

  /**
   * Must be int from 0 to 100 with default 50.
   * Allows specifying the order of the items in the completion popup.
   * The greater the number, the closer the item will be to the first place.
   */
  val priority: Int

  /**
   * Custom icon instead of autodetected from [type].
   */
  val icon: Icon?
}