package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

/**
 * Base interface for shell command, option, and argument value suggestions.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ShellCompletionSuggestion {
  /**
   * The string to be shown in the completion popup and inserted on completion.
   */
  val name: @NonNls String

  /**
   * Used for now mostly to automatically configure the icon.
   */
  val type: ShellSuggestionType

  /**
   * The string to be shown in the completion popup instead of [name] if specified.
   */
  val displayName: @NonNls String?

  /**
   * Text to be shown in the documentation popup.
   *
   * **Documentation popup currently is not supported in the Reworked Terminal.**
   */
  val description: @Nls String?

  /**
   * The string to be inserted on completion instead of [name] if specified.
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

  /**
   * Position inside the [ShellRuntimeContext.typedPrefix] string after which this suggestion should be applied.
   * For example,
   * 1. If typed prefix is `bra` and suggestion name is `branch`, then the replacement index should be `0`,
   * because we need to fully replace the `bra` prefix with `branch`.
   * 2. If typed prefix is `foo/b` and suggestion name is `bar` (we want to suggest the part of the path after `/`),
   * then the replacement index should be `4`.
   */
  val prefixReplacementIndex: Int

  /**
   * If true, then this suggestion won't be shown in the completion popup.
   * It may be necessary to specify that this suggestion is also a valid value for the argument.
   * So parser will be able to distinguish it and not mark it as something unknown.
   *
   * For example, if there is a directory suggestion, then it may have a trailing file separator or may not.
   * Both options are acceptable, but only one of them should be shown in the completion popup.
   */
  val isHidden: Boolean

  /**
   * If true, the [name] or [insertValue] will be automatically escaped according to the shell syntax
   * (e.g., handling whitespaces or special characters) before being inserted.
   *
   * If false, the value is inserted exactly as provided.
   */
  val shouldEscape: Boolean
}
