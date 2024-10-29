package com.intellij.terminal.completion.spec

import org.jetbrains.annotations.ApiStatus

/**
 * Enum for specifying the type of [ShellCompletionSuggestion].
 * Used for now only to choose the right icon for the completion item.
 */
@ApiStatus.Experimental
enum class ShellSuggestionType {
  COMMAND,
  OPTION,
  ARGUMENT,
  FILE,
  FOLDER
}
